package com.jetbrains.bigdatatools.kafka.client

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.jetbrains.bigdatatools.connection.tunnel.BdtSshTunnelConnectionUtils
import com.jetbrains.bigdatatools.connection.tunnel.BdtSshTunnelService
import com.jetbrains.bigdatatools.connection.tunnel.model.getTunnelDataOrNull
import com.jetbrains.bigdatatools.kafka.model.ConsumerGroupPresentable
import com.jetbrains.bigdatatools.kafka.model.TopicConfig
import com.jetbrains.bigdatatools.kafka.model.TopicPresentable
import com.jetbrains.bigdatatools.kafka.producer.client.KafkaProducerClient
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.kafka.rfs.KafkaPropertySource
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.monitoring.connection.MonitoringClient
import com.jetbrains.bigdatatools.settings.components.BdtPropertyComponent
import com.jetbrains.bigdatatools.settings.connections.Property
import com.jetbrains.bigdatatools.util.BdIdeRegistryUtil
import com.jetbrains.bigdatatools.util.executeOnPooledThread
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.admin.*
import org.apache.kafka.common.config.ConfigResource
import java.io.File
import java.time.Duration
import java.util.*


class KafkaClient(project: Project?,
                  val connectionData: KafkaConnectionData,
                  val testConnection: Boolean) : MonitoringClient(project) {
  internal val kafkaProps by lazy {
    getKafkaProps(connectionData)
  }

  private var kafkaAdmin: AdminClient? = null
  private val kafkaAdminNotNull: AdminClient
    get() = kafkaAdmin ?: error("Kafka Admin Client is not inited")

  fun createProducerClient() = KafkaProducerClient(client = this)

  override fun dispose() = executeOnPooledThread {
    try {
      kafkaAdmin?.close(Duration.ofSeconds(10))
    }
    catch (t: Throwable) {
      logger.warn("Cannot close kafka client", t)
    }

    BdtSshTunnelService.deleteIfExists(project, connectionData.innerId, connectionData.getTunnelDataOrNull(), testConnection)
  }

  override fun getRealUri(): String = kafkaProps.getProperty(SERVER_URL) ?: "<NOT_FOUND>"

  override fun checkConnectionInner() {
    val kafkaAdmin = kafkaAdmin ?: error("Admin Client is not initialized")
    val clusterOptions = DescribeClusterOptions().timeoutMs(BdIdeRegistryUtil.RFS_DEFAULT_TIMEOUT)
    kafkaAdmin.describeCluster(clusterOptions).clusterId().get()
  }

  override fun connectInner(calledByUser: Boolean) {
    val localPort = BdtSshTunnelService.createIfRequired(project, connectionData.innerId, connectionData.getTunnelData(), testConnection)
    if (localPort != null) {
      val urlForTunnel = BdtSshTunnelConnectionUtils.getUrlForTunnel(connectionData.uri, localPort)
      kafkaProps.setProperty(SERVER_URL, urlForTunnel)
    }

    if (kafkaAdmin == null) {
      val contextCL = Thread.currentThread().contextClassLoader
      try {
        Thread.currentThread().contextClassLoader = this::class.java.classLoader
        kafkaAdmin = AdminClient.create(kafkaProps)
      }
      finally {
        Thread.currentThread().contextClassLoader = contextCL
      }
    }

    checkConnectionInner()
  }

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
        val propertyFilePath = connectionData.propertyFilePath ?: error(
          KafkaMessagesBundle.message("property.file.is.not.found", ""))
        val filePath = File(propertyFilePath).toPath()
        val vf = VirtualFileManager.getInstance().findFileByNioPath(filePath) ?: error(
          KafkaMessagesBundle.message("property.file.is.not.found", filePath))
        vf.inputStream.bufferedReader().readText()
      }
    }
    BdtPropertyComponent.parseProperties(properties).forEach {
      props[it.name ?: ""] = it.value ?: ""
    }

    return props
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

  companion object {
    private val logger = Logger.getInstance(this::class.java)
    private const val SERVER_URL = "bootstrap.servers"
  }
}