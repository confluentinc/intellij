package com.jetbrains.bigdatatools.kafka.client

import com.intellij.bigdatatools.aws.ui.external.AwsSettingsComponentForKafka
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.io.await
import com.intellij.util.net.NetUtils
import com.jetbrains.bigdatatools.common.connection.exception.BdtConnectionException
import com.jetbrains.bigdatatools.common.connection.exception.BdtHostUnavailableException
import com.jetbrains.bigdatatools.common.connection.exception.BdtUnexpectedConnectionException
import com.jetbrains.bigdatatools.common.connection.tunnel.BdtSshTunnelService.createIfRequired
import com.jetbrains.bigdatatools.common.monitoring.connection.MonitoringClient
import com.jetbrains.bigdatatools.common.settings.components.BdtPropertyComponent
import com.jetbrains.bigdatatools.common.settings.connections.Property
import com.jetbrains.bigdatatools.common.util.BdIdeRegistryUtil
import com.jetbrains.bigdatatools.common.util.BdtUrlUtils
import com.jetbrains.bigdatatools.common.util.withPluginClassLoader
import com.jetbrains.bigdatatools.kafka.model.*
import com.jetbrains.bigdatatools.kafka.producer.client.KafkaProducerClient
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryType
import com.jetbrains.bigdatatools.kafka.registry.confluent.ConfluentRegistryClient
import com.jetbrains.bigdatatools.kafka.registry.glue.BdtGlueRegistryClient
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.kafka.rfs.KafkaPropertySource
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.admin.*
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

    createIfRequired(project, connectionData.getTunnelData(), connectionData.uri, connectionData.innerId, testConnection)
      ?.let { tunnelHandler ->
        Disposer.register(this, tunnelHandler)
        val urlForTunnel = tunnelHandler.tunnelledUri.split("//:").last()
        kafkaProps.setProperty(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, urlForTunnel)
      }
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
        confluentRegistryClient = ConfluentRegistryClient.createFor(project, connectionData, testConnection)?.also {
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

    longBrokerCheckConnection()
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
    return kafkaAdminNotNull.listConsumerGroups().all().get().map {
      ConsumerGroupPresentable(state = it.state().getOrNull() ?: ConsumerGroupState.UNKNOWN, consumerGroup = it.groupId())
    }.sortedBy { it.consumerGroup }
  }

  fun getDetailedConsumerGroups(consumerGroupsIds: List<String>): List<ConsumerGroupPresentable> {
    val detailedGroups = kafkaAdminNotNull.describeConsumerGroups(consumerGroupsIds).all().get()
    return detailedGroups.map { (_, detailedGroup) ->
      BdtKafkaMapper.mapToConsumerGroup(detailedGroup)
    }.sortedBy { it.consumerGroup }
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

  private fun describeTopics(topicNames: List<String>): Pair<List<TopicDescription>, List<String>> = try {
    val allTopicNames = kafkaAdminNotNull.describeTopics(topicNames.toMutableList()).allTopicNames()
    allTopicNames.get().values.toList() to emptyList()
  }
  catch (t: Throwable) {
    val topics = mutableListOf<TopicDescription>()
    val nonParsed = mutableListOf<String>()

    topicNames.map {
      try {
        val topicDescription = kafkaAdminNotNull.describeTopics(listOf(it)).allTopicNames().get().values.firstOrNull()
        if (topicDescription != null)
          topics.add(topicDescription)
        else
          nonParsed.add(it)
      }
      catch (t: Throwable) {
        logger.warn(t)
        nonParsed.add(it)
      }
    }

    topics to nonParsed
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
    return filteredNames.sorted()
  }

  private fun getKafkaProps(connectionData: KafkaConnectionData): Properties {
    val defaultProps = listOf(
      Property(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, connectionData.uri.removeSuffix("/")),
      Property(CommonClientConfigs.CONNECTIONS_MAX_IDLE_MS_CONFIG, 10000.toString()),
      Property(CommonClientConfigs.REQUEST_TIMEOUT_MS_CONFIG, BdIdeRegistryUtil.RFS_DEFAULT_TIMEOUT.toString()),
      Property(CommonClientConfigs.RETRY_BACKOFF_MS_CONFIG, 30000.toString()),
      Property(CommonClientConfigs.METADATA_MAX_AGE_CONFIG, 60000.toString())
    )

    val props = Properties()

    defaultProps.forEach {
      props[it.name ?: ""] = it.value ?: ""
    }

    val properties = when (connectionData.propertySource) {
      KafkaPropertySource.DIRECT -> connectionData.properties
      KafkaPropertySource.FILE -> {
        val propertyFilePath = connectionData.propertyFilePath ?: error(KafkaMessagesBundle.message("property.file.is.not.found", ""))
        loadPropertyFile(propertyFilePath)
      }
    }

    when (connectionData.registryType) {
      KafkaRegistryType.NONE -> {}
      KafkaRegistryType.CONFLUENT -> {
        BdtPropertyComponent.parseProperties(connectionData.registryProperties).forEach {
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

  private fun longBrokerCheckConnection() = try {
    val url = kafkaProps.getProperty(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG)
              ?: throw BdtUnexpectedConnectionException(null, KafkaMessagesBundle.message("connection.is.not.found.in.config",
                                                                                          CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG))
    checkUrlAvailability(url)
    try {
      val kafkaAdmin = kafkaAdmin ?: error(KafkaMessagesBundle.message("connection.admin.is.not.initialized"))
      val clusterOptions = DescribeClusterOptions().timeoutMs(BdIdeRegistryUtil.RFS_DEFAULT_TIMEOUT)
      kafkaAdmin.describeCluster(clusterOptions).clusterId().get()
    }
    catch (t: Throwable) {
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
    return this.sortedBy { it.name.lowercase() }
  }

  fun getTopicConfig(topicName: String): List<TopicConfig> = loadTopicConfigs(listOf(topicName)).values.first()

  companion object {
    fun loadPropertyFile(propertyFilePath: String): String {
      val filePath = File(propertyFilePath).toPath()
      val vf = VirtualFileManager.getInstance().findFileByNioPath(filePath) ?: error(
        KafkaMessagesBundle.message("property.file.is.not.found", filePath))
      return vf.inputStream.bufferedReader().readText()
    }
  }
}