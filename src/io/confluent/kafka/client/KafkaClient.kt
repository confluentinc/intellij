package io.confluent.kafka.client

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.io.await
import com.intellij.util.net.NetUtils
import io.confluent.kafka.aws.ui.external.AwsSettingsComponentForKafka
import io.confluent.kafka.core.connection.exception.BdtConnectionException
import io.confluent.kafka.core.connection.exception.BdtHostUnavailableException
import io.confluent.kafka.core.connection.exception.BdtUnexpectedConnectionException
import io.confluent.kafka.core.connection.tunnel.BdtSshTunnelService.createIfRequired
import io.confluent.kafka.core.monitoring.connection.MonitoringClient
import io.confluent.kafka.core.settings.components.BdtPropertyComponent
import io.confluent.kafka.core.settings.connections.Property
import io.confluent.kafka.core.util.BdIdeRegistryUtil
import io.confluent.kafka.core.util.BdtUrlUtils
import io.confluent.kafka.core.util.withPluginClassLoader
import io.confluent.kafka.model.*
import io.confluent.kafka.producer.client.KafkaProducerClient
import io.confluent.kafka.registry.KafkaRegistryType
import io.confluent.kafka.registry.confluent.ConfluentRegistryClient
import io.confluent.kafka.registry.glue.BdtGlueRegistryClient
import io.confluent.kafka.rfs.KafkaConfigurationSource
import io.confluent.kafka.rfs.KafkaConnectionData
import io.confluent.kafka.rfs.KafkaPropertySource
import io.confluent.kafka.toolwindow.config.KafkaToolWindowSettings
import io.confluent.kafka.util.KafkaMessagesBundle
import io.confluent.kafka.util.KafkaSslUtils
import kotlinx.coroutines.TimeoutCancellationException
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.admin.*
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.ConsumerGroupState
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.config.ConfigResource
import org.apache.kafka.common.config.SaslConfigs
import java.io.File
import java.util.*
import kotlin.jvm.optionals.getOrNull


class KafkaClient(project: Project?,
                  val connectionData: KafkaConnectionData,
                  private val testConnection: Boolean) : MonitoringClient(project) {
  internal val kafkaProps by lazy {
    getKafkaProps(connectionData)
  }

  private var kafkaAdmin: BdtKafkaAdminClient? = null
  private val kafkaAdminNotNull
    get() = kafkaAdmin ?: error("Kafka Admin Client is not inited")

  var confluentRegistryClient: ConfluentRegistryClient? = null
    private set

  var glueRegistryClient: BdtGlueRegistryClient? = null
    private set


  override fun dispose() {}

  override fun connectInner(calledByUser: Boolean) {
    disposeKafkaAdminClient()

    val tunnelData = connectionData.getTunnelData()
    val urls = kafkaProps.getProperty(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG)
    if (tunnelData.isEnabled && urls.contains(",")) {
      throw BdtConnectionException(KafkaMessagesBundle.message("connection.error.tunnel.for.sinlgle.broker"))
    }
    createIfRequired(project, tunnelData, urls,
                     connectionData.innerId, testConnection)
      ?.let { tunnelHandler ->
        Disposer.register(this, tunnelHandler)
        val urlForTunnel = tunnelHandler.tunnelledUri.split("//:").last()
        kafkaProps.setProperty(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, urlForTunnel)
      }

    KafkaSslUtils.addMigratedToProperties(kafkaProps)

    withPluginClassLoader {
      setSystemPropertiesForAwsIam()
      try {
        kafkaAdmin = KafkaClientBuilder.createAdminClient(kafkaProps)
        kafkaAdmin?.let { Disposer.register(this, it) }
      }
      catch (t: Throwable) {
        disposeKafkaAdminClient()
        throw t
      }
    }

    confluentRegistryClient?.let {
      Disposer.dispose(it)
    }

    glueRegistryClient?.let {
      Disposer.dispose(it)
    }

    confluentRegistryClient = null
    glueRegistryClient = null


    when (connectionData.registryType) {
      KafkaRegistryType.NONE -> {}
      KafkaRegistryType.CONFLUENT -> {
        confluentRegistryClient = ConfluentRegistryClient.createFor(project, connectionData, testConnection).also {
          Disposer.register(this, it)
        }
      }
      KafkaRegistryType.AWS_GLUE -> {
        glueRegistryClient = createGlueClient()?.also {
          Disposer.register(this, it)
        }
        glueRegistryClient?.connect(calledByUser)
      }
    }

    runBlockingCancellable {
      longBrokerCheckConnection()
    }
  }

  override fun checkConnectionInner() {
    val urlsString = kafkaProps.getProperty(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG)
                     ?: throw BdtUnexpectedConnectionException(null, KafkaMessagesBundle.message("connection.is.not.found.in.config",
                                                                                                 CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG))
    try {
      checkUrlAvailability(urlsString)
    }
    catch (t: Throwable) {
      disposeKafkaAdminClient()
      throw t
    }

    checkRegistryClient()
  }

  fun createProducerClient() = KafkaProducerClient(client = this)

  override fun getRealUri(): String = kafkaProps.getProperty(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG) ?: "<NOT_FOUND>"


  suspend fun clearPartitions(partitionList: List<BdtTopicPartition>) {
    val partitions = partitionList.map { TopicPartition(it.topic, it.partitionId) }
    val offsets = listOffsets(partitions, OffsetSpec.latest())

    val deleteRequest = offsets.entries.associate { it.key to RecordsToDelete.beforeOffset(it.value.offset()) }
    kafkaAdminNotNull.deleteRecords(deleteRequest).all().await()
  }

  fun getConsumerGroups(): List<ConsumerGroupPresentable> {
    val result = kafkaAdminNotNull.listConsumerGroups().valid().get().filter { !it.groupId().endsWith("/") }
    val config = KafkaToolWindowSettings.getInstance().getOrCreateConfig(connectionData.innerId)
    return result.map {
      ConsumerGroupPresentable(state = it.state().getOrNull() ?: ConsumerGroupState.UNKNOWN, consumerGroup = it.groupId(),
                               isFavorite = config.consumerGroupPined.contains(it.groupId()))
    }.sortedWith(compareByDescending<ConsumerGroupPresentable> { it.isFavorite }.thenBy { it.consumerGroup.lowercase() })
  }

  suspend fun listConsumerGroupOffsets(consumerGroup: String): Map<TopicPartition, OffsetAndMetadata> {
    val offsetResults = kafkaAdminNotNull.listConsumerGroupOffsets(consumerGroup).all().await().entries.firstOrNull()?.value ?: emptyMap()
    return offsetResults
  }

  fun getConsumerGroupOffsets(consumerGroup: String) =
    kafkaAdminNotNull.listConsumerGroupOffsets(consumerGroup).partitionsToOffsetAndMetadata().get()?.toMap() ?: mapOf()

  fun getTopics(listInternal: Boolean): List<TopicPresentable> {
    val topicNames = getTopicNames(listInternal)
    return topicNames.map { TopicPresentable(name = it) }.sortedTopics()
  }

  suspend fun getDetailedTopicsInfo(topicNames: List<String>): List<TopicPresentable> {
    val (describedTopics, nonParsed) = describeTopics(topicNames)

    val partitionsInfos = requestTopicOffsets(describedTopics)
    val parsedInternal = describedTopics.map { description ->
      BdtKafkaMapper.topicDescriptionToInternalTopic(description, partitions = partitionsInfos[description.name()] ?: emptyList())
    }

    val nonParsedInternal = nonParsed.map { BdtKafkaMapper.mockInternalTopic(it) }
    return (parsedInternal + nonParsedInternal).toList().sortedTopics()
  }

  suspend fun loadLatestOffsets(topicPartitions: Set<TopicPartition>): Map<TopicPartition, Long> {
    if (topicPartitions.isEmpty())
      return emptyMap()

    val request = kafkaAdminNotNull.listOffsets(topicPartitions.associateWith { OffsetSpec.latest() }.toMutableMap())
    return topicPartitions.mapNotNull {
      try {
        it to request.partitionResult(it).await().offset()
      }
      catch (t: Throwable) {
        thisLogger().warn("Cannot load offset for $it", t)
        null
      }
    }.toMap()
  }

  private suspend fun requestTopicOffsets(topics: List<TopicDescription>): Map<String, List<BdtTopicPartition>> {
    val requestPartitions = topics.flatMap { desc ->
      desc.partitions().map { TopicPartition(desc.name(), it.partition()) }
    }
    val earliestOffsetsFuture = kafkaAdminNotNull.listOffsets(
      requestPartitions.associateWith { OffsetSpec.earliest() }.toMutableMap()).all()
    val latestOffsetsFuture = kafkaAdminNotNull.listOffsets(requestPartitions.associateWith { OffsetSpec.latest() }.toMutableMap()).all()
    val earliestOffsets = earliestOffsetsFuture.await()
    val latestOffsets = latestOffsetsFuture.await()

    return topics.associate { topic ->
      val partitions = topic.partitions().map { partition ->
        val replicas: List<InternalReplica> = partition.replicas().filterNotNull().map {
          InternalReplica(it.id(), partition.leader()?.id() != it.id(), partition.isr()?.contains(it) == true)
        }
        BdtTopicPartition(topic = topic.name(),
                          leader = partition.leader()?.id(),
                          partitionId = partition.partition(),
                          inSyncReplicasCount = partition.isr().size,
                          replicas = partition.replicas()?.joinToString(separator = ", ") { it.idString() } ?: "",
                          startOffset = earliestOffsets[TopicPartition(topic.name(), partition.partition())]?.offset(),
                          endOffset = latestOffsets[TopicPartition(topic.name(), partition.partition())]?.offset(),
                          internalReplicas = replicas)

      }
      topic.name() to partitions
    }
  }

  fun createTopic(name: String, numPartition: Int?, replicationFactor: Int?) {
    val admin = kafkaAdmin ?: error("Kafka admin is not inited")

    val newTopic = NewTopic(name, Optional.ofNullable(numPartition), Optional.ofNullable(replicationFactor?.toShort()))
    val createTopics = admin.createTopics(listOf(newTopic))
    createTopics?.topicId(name)?.get()
  }

  fun deleteTopic(name: String) {
    val admin = kafkaAdmin ?: error("Kafka admin is not inited")
    val deleteTopicsResult = admin.deleteTopics(listOf(name))
    deleteTopicsResult.all().get()
  }

  private suspend fun describeTopics(topicNames: List<String>): Pair<List<TopicDescription>, List<String>> {
    // probable here some performance issue
    val futures = kafkaAdminNotNull.describeTopics(topicNames.toMutableList()).topicNameValues()
    val topics = mutableListOf<TopicDescription>()
    val nonParsed = mutableListOf<String>()

    for ((name, topicDetailsFuture) in futures) {
      try {
        topics.add(topicDetailsFuture.await())
      }
      catch (t: Throwable) {
        logger.warn(t)
        nonParsed.add(name)
      }
    }
    return topics to nonParsed
  }

  private fun loadTopicConfigs(topicNames: List<String>): Map<String, List<TopicConfig>> {
    val resources = topicNames.map {
      ConfigResource(ConfigResource.Type.TOPIC, it)
    }

    val describedConfigs = kafkaAdminNotNull.describeConfigs(resources).values().map {
      try {
        it.key.name() to it.value.get()
      }
      catch (t: Throwable) {
        logger.warn(t)
        null
      }
    }.filterNotNull()

    return describedConfigs.associate { config ->
      val configEntry = config.second.entries()
      val internalConfigs = configEntry.map { BdtKafkaMapper.mapToInternalTopicConfig(it) }
      config.first to internalConfigs
    }
  }

  private fun getTopicNames(listInternal: Boolean): List<String> {
    val listTopicsOptions = ListTopicsOptions()
    listTopicsOptions.listInternal(listInternal)
    val names = kafkaAdminNotNull.listTopics(listTopicsOptions).names().get() ?: emptySet()
    val filteredNames = if (listInternal)
      names
    else
      names.filter { !it.startsWith("_") }
    return filteredNames.sorted().filter { !it.endsWith("/") }
  }

  private fun getKafkaProps(connectionData: KafkaConnectionData): Properties {
    val defaultProps = listOf(
      Property(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, connectionData.uri.removeSuffix("/")),
      Property(CommonClientConfigs.REQUEST_TIMEOUT_MS_CONFIG, maxOf(BdIdeRegistryUtil.RFS_DEFAULT_TIMEOUT, 30_000).toString()),
      Property(CommonClientConfigs.RETRIES_CONFIG, 3.toString()),
      Property(CommonClientConfigs.DEFAULT_API_TIMEOUT_MS_CONFIG, maxOf(3 * BdIdeRegistryUtil.RFS_DEFAULT_TIMEOUT, 30_000).toString())
    )

    val props = Properties()

    defaultProps.forEach {
      props[it.name ?: ""] = it.value ?: ""
    }

    val properties = if (connectionData.propertySource == KafkaPropertySource.FILE && connectionData.brokerConfigurationSource == KafkaConfigurationSource.FROM_PROPERTIES) {
      val propertyFilePath = connectionData.propertyFilePath ?: error(KafkaMessagesBundle.message("property.file.is.not.found", ""))
      loadPropertyFile(propertyFilePath)
    }
    else {
      connectionData.secretProperties
    }

    when (connectionData.registryType) {
      KafkaRegistryType.NONE -> {}
      KafkaRegistryType.CONFLUENT -> {
        BdtPropertyComponent.parseProperties(connectionData.secretRegistryProperties).forEach {
          props[it.name ?: ""] = it.value ?: ""
        }
      }
      KafkaRegistryType.AWS_GLUE -> {}
    }


    BdtPropertyComponent.parseProperties(properties).forEach {
      props[it.name ?: ""] = it.value ?: ""
    }

    //Kafka load string by own classloader which is not see classes from BDT
    val saslCallback = props[SaslConfigs.SASL_CLIENT_CALLBACK_HANDLER_CLASS]
    if (saslCallback is String) {
      try {
        props[SaslConfigs.SASL_CLIENT_CALLBACK_HANDLER_CLASS] = this::class.java.classLoader.loadClass(saslCallback)
      }
      catch (_: Throwable) {
        //Ignore
      }
    }
    return props
  }

  private fun checkRegistryClient() {
    try {
      confluentRegistryClient?.checkConnection()
      glueRegistryClient?.checkConnection()
    }
    catch (t: Throwable) {
      throw BdtConnectionException(KafkaMessagesBundle.message("connection.kafka.registry.is.not.available"), t)
    }
  }

  private fun checkUrlAvailability(url: String) {
    val urls = url.split(",").map { it.trim() }
    val canConnect = urls.any {
      val urlObject = BdtUrlUtils.convertToUrlObject(it)
      NetUtils.canConnectToRemoteSocket(urlObject.host, urlObject.port)
    }

    if (!canConnect) {
      throw BdtHostUnavailableException(url)
    }
  }

  private suspend fun longBrokerCheckConnection() = try {
    val url = kafkaProps.getProperty(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG)
              ?: throw BdtUnexpectedConnectionException(null, KafkaMessagesBundle.message("connection.is.not.found.in.config",
                                                                                          CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG))
    checkUrlAvailability(url)
    try {
      val kafkaAdmin = kafkaAdmin ?: error(KafkaMessagesBundle.message("connection.admin.is.not.initialized"))
      val clusterOptions = DescribeClusterOptions().timeoutMs(BdIdeRegistryUtil.RFS_DEFAULT_TIMEOUT)
      kafkaAdmin.describeCluster(clusterOptions).clusterId().await()
    }
    catch (t: TimeoutCancellationException) {
      throw BdtConnectionException(KafkaMessagesBundle.message("connection.check.port.success.but.next.error"), t)
    }
    catch (t: InterruptedException) {
      throw BdtConnectionException(KafkaMessagesBundle.message("connection.check.port.success.but.next.error"), t)
    }
  }
  catch (t: Throwable) {
    disposeKafkaAdminClient()
    throw t
  }

  private fun setSystemPropertiesForAwsIam() {
    if (kafkaProps.getProperty(SaslConfigs.SASL_MECHANISM) != AwsSettingsComponentForKafka.AWS_MECHANISM)
      return
    val accessKey = kafkaProps.getProperty(AwsSettingsComponentForKafka.AWS_ACCESS_KEY)?.ifBlank { null }?.trim()
    val secretKey = kafkaProps.getProperty(AwsSettingsComponentForKafka.AWS_SECRET_KEY)?.ifBlank { null }?.trim()
    accessKey?.let { System.setProperty(AwsSettingsComponentForKafka.AWS_ACCESS_KEY, it) }
    secretKey?.let { System.setProperty(AwsSettingsComponentForKafka.AWS_SECRET_KEY, it) }
  }

  private suspend fun listOffsets(partitions: List<TopicPartition>,
                                  offsetSpec: OffsetSpec): MutableMap<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> {
    val offsets = partitions.associateWith { offsetSpec }
    return kafkaAdminNotNull.listOffsets(offsets).all().await() ?: mutableMapOf()
  }

  private fun createGlueClient(): BdtGlueRegistryClient? {
    val awsSettingsInfo = connectionData.loadAwsGlueSettings() ?: return null
    return BdtGlueRegistryClient(project,
                                 connectionData.getGlueRegistryOrDefault(),
                                 awsSettingsInfo)
  }

  private fun disposeKafkaAdminClient() {
    kafkaAdmin?.let {
      Disposer.dispose(it)
    }

    kafkaAdmin = null
  }

  private fun List<TopicPresentable>.sortedTopics(): List<TopicPresentable> {
    val kafkaSettings = KafkaToolWindowSettings.getInstance()
    val config = kafkaSettings.getOrCreateConfig(connectionData.innerId)
    val topics = this.map { topic -> topic.copy(isFavorite = config.topicsPined.contains(topic.name)) }

    val finalTopics = if (kafkaSettings.showFavoriteTopics) topics.filter { it.isFavorite } else topics
    // sort firstly by favorite topics then by name
    return finalTopics.sortedWith(compareByDescending<TopicPresentable> { it.isFavorite }.thenBy { it.name.lowercase() })
  }

  fun getTopicConfig(topicName: String): List<TopicConfig> = loadTopicConfigs(listOf(topicName)).values.first()

  fun deleteConsumerGroup(name: String) {
    kafkaAdminNotNull.deleteConsumerGroups(listOf(name))
  }

  suspend fun getOffsetsForDate(partitions: List<TopicPartition>, timestamp: Long): Map<TopicPartition, Long> {
    val request = partitions.associateWith { OffsetSpec.forTimestamp(timestamp) }
    val results = kafkaAdminNotNull.listOffsets(request).all().await()
    return results.map { it.key to it.value.offset() }.toMap()
  }

  suspend fun resetOffsets(consumeGroupId: String, offsets: Map<TopicPartition, OffsetAndMetadata>) {
    kafkaAdminNotNull.alterConsumerGroupOffsets(consumeGroupId, offsets).all().await()
  }

  companion object {
    fun loadPropertyFile(propertyFilePath: String): String {
      val filePath = File(propertyFilePath).toPath()
      val vf = VirtualFileManager.getInstance().findFileByNioPath(filePath) ?: error(
        KafkaMessagesBundle.message("property.file.is.not.found", filePath))
      return vf.inputStream.bufferedReader().readText()
    }
  }
}