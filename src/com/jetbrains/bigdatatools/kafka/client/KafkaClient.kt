package com.jetbrains.bigdatatools.kafka.client

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.net.NetUtils
import com.jetbrains.bigdatatools.aws.common.ui.external.AwsSettingsForKafka
import com.jetbrains.bigdatatools.common.connection.exception.BdtConnectionException
import com.jetbrains.bigdatatools.common.connection.exception.BdtHostUnavailableException
import com.jetbrains.bigdatatools.common.connection.exception.BdtUnexpectedConnectionException
import com.jetbrains.bigdatatools.common.monitoring.connection.MonitoringClient
import com.jetbrains.bigdatatools.common.settings.components.BdtPropertyComponent
import com.jetbrains.bigdatatools.common.settings.connections.Property
import com.jetbrains.bigdatatools.common.util.BdIdeRegistryUtil
import com.jetbrains.bigdatatools.common.util.BdtUrlUtils
import com.jetbrains.bigdatatools.common.util.withPluginClassLoader
import com.jetbrains.bigdatatools.kafka.model.ConsumerGroupPresentable
import com.jetbrains.bigdatatools.kafka.model.TopicConfig
import com.jetbrains.bigdatatools.kafka.model.TopicPresentable
import com.jetbrains.bigdatatools.kafka.producer.client.KafkaProducerClient
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryType
import com.jetbrains.bigdatatools.kafka.registry.confluent.ConfluentRegistryClient
import com.jetbrains.bigdatatools.kafka.registry.glue.BdtGlueRegistryClient
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.kafka.rfs.KafkaPropertySource
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.admin.DescribeClusterOptions
import org.apache.kafka.clients.admin.ListTopicsOptions
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.admin.TopicDescription
import org.apache.kafka.common.config.ConfigResource
import org.apache.kafka.common.config.SaslConfigs
import java.io.File
import java.util.*


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
    kafkaAdmin?.let {
      Disposer.dispose(it)
    }

    kafkaAdmin = null

    withPluginClassLoader {
      setSystemPropertiesForAwsIam()
      kafkaAdmin = KafkaClientBuilder.createAdminClient(kafkaProps)
      kafkaAdmin?.let { Disposer.register(this, it) }
    }

    confluentRegistryClient?.let {
      Disposer.dispose(it)
    }

    confluentRegistryClient = null

    when (connectionData.registryType) {
      KafkaRegistryType.NONE -> {}
      KafkaRegistryType.CONFLUENT -> {
        confluentRegistryClient = ConfluentRegistryClient.createFor(project, connectionData, testConnection)
      }
      KafkaRegistryType.AWS_GLUE -> {
        glueRegistryClient = createGlueClient()
        glueRegistryClient?.connect(calledByUser)
        glueRegistryClient?.let {
          Disposer.register(this, it)
        }
      }
    }
    confluentRegistryClient = ConfluentRegistryClient.createFor(project, connectionData, testConnection)
    confluentRegistryClient?.let {
      Disposer.register(this, it)
    }

    longCheckConnection()
  }


  override fun checkConnectionInner() {
    val urlsString = kafkaProps.getProperty(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG)
                     ?: throw BdtUnexpectedConnectionException(null, KafkaMessagesBundle.message("connection.is.not.found.in.config",
                                                                                                 CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG))
    checkUrlAvailability(urlsString)
  }

  fun createProducerClient() = KafkaProducerClient(client = this)


  override fun getRealUri(): String = kafkaProps.getProperty(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG) ?: "<NOT_FOUND>"

  fun getConsumerGroups(): List<ConsumerGroupPresentable> {
    val consumerGroupsIds: List<String> = kafkaAdminNotNull.listConsumerGroups().all().get().map {
      it.groupId()
    }
    val detailedGroups = kafkaAdminNotNull.describeConsumerGroups(consumerGroupsIds).all().get()
    return detailedGroups.map { (_, detailedGroup) ->
      BdtKafkaMapper.mapToConsumerGroup(detailedGroup)
    }
  }

  fun getConsumerGroupOffsets(consumerGroup: String) =
    kafkaAdminNotNull.listConsumerGroupOffsets(consumerGroup).partitionsToOffsetAndMetadata().get()?.toMap() ?: mapOf()

  fun getTopics(listInternal: Boolean): List<TopicPresentable> {
    val topicNames = getTopicNames(listInternal)
    val (describedTopics, nonParsed) = describeTopics(topicNames)
    val loadedTopicConfig = try {
      loadTopicConfigs(topicNames)
    }
    catch (t: Throwable) {
      logger.warn("Cannot load topic configs, ignore it", t)
      emptyMap()
    }
    val parsedInternal = describedTopics.map { BdtKafkaMapper.topicDescriptionToInternalTopic(it) }
    val nonParsedInternal = nonParsed.map { BdtKafkaMapper.mockInternalTopic(it) }
    return BdtKafkaMapper.mergeWithConfigs(parsedInternal + nonParsedInternal, loadedTopicConfig).values.toList()
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
    kafkaAdminNotNull.describeTopics(topicNames.toMutableList()).allTopicNames().get().values.toList() to emptyList()
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
      confluentRegistryClient?.allSubjects
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

  private fun longCheckConnection() {
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
      throw BdtConnectionException(KafkaMessagesBundle.message("connection.check.port.success.but.next.error", url), t)
    }

    checkRegistryClient()
  }

  private fun setSystemPropertiesForAwsIam() {
    if (kafkaProps.getProperty(SaslConfigs.SASL_MECHANISM) != AwsSettingsForKafka.AWS_MECHANISM)
      return
    val accessKey = kafkaProps.getProperty(AwsSettingsForKafka.AWS_ACCESS_KEY)?.ifBlank { null }?.trim()
    val secretKey = kafkaProps.getProperty(AwsSettingsForKafka.AWS_SECRET_KEY)?.ifBlank { null }?.trim()
    accessKey?.let { System.setProperty(AwsSettingsForKafka.AWS_ACCESS_KEY, it) }
    secretKey?.let { System.setProperty(AwsSettingsForKafka.AWS_SECRET_KEY, it) }
  }


  private fun createGlueClient(): BdtGlueRegistryClient? {
    val awsSettingsInfo = connectionData.loadAwsGlueSettings() ?: return null
    return BdtGlueRegistryClient(project, awsSettingsInfo)
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