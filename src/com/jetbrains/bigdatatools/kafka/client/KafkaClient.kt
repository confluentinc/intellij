package com.jetbrains.bigdatatools.kafka.client

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.jetbrains.bigdatatools.connection.tunnel.BdtSshTunnelConnectionUtils
import com.jetbrains.bigdatatools.connection.tunnel.BdtSshTunnelService
import com.jetbrains.bigdatatools.connection.tunnel.model.getTunnelDataOrNull
import com.jetbrains.bigdatatools.kafka.model.ConsumerGroupPresentable
import com.jetbrains.bigdatatools.kafka.model.TopicConfig
import com.jetbrains.bigdatatools.kafka.model.TopicPresentable
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.monitoring.connection.MonitoringClient
import com.jetbrains.bigdatatools.settings.connections.Property
import com.jetbrains.bigdatatools.settings.fields.PropertiesFieldComponent
import com.jetbrains.bigdatatools.util.executeOnPooledThread
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.ListTopicsOptions
import org.apache.kafka.clients.admin.TopicDescription
import org.apache.kafka.common.config.ConfigResource
import java.time.Duration
import java.util.*

class KafkaClient(project: Project?,
                  private val connectionData: KafkaConnectionData,
                  val testConnection: Boolean) : MonitoringClient(project) {
  private val kafkaProps = getKafkaProps(connectionData)
  private var kafkaAdmin: AdminClient? = null
  private val kafkaAdminNotNull: AdminClient
    get() = kafkaAdmin ?: error("Kafka Admin Client is not inited")


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
    kafkaAdmin.describeCluster().controller().get()
  }

  override fun connectInner() {
    val localPort = BdtSshTunnelService.createIfRequired(project, connectionData.innerId, connectionData.getTunnelData(), testConnection)
    if (localPort != null) {
      val urlForTunnel = BdtSshTunnelConnectionUtils.getUrlForTunnel(connectionData.uri, localPort)
      kafkaProps.setProperty(SERVER_URL, urlForTunnel)
    }
    kafkaAdmin = AdminClient.create(kafkaProps)
    checkConnectionInner()
  }

  fun getConsumerGroups(): List<ConsumerGroupPresentable> {
    val consumerGroupsIds: List<String> = kafkaAdminNotNull. listConsumerGroups ().all().get().map {
      it.groupId()
    }
    val detailedGroups = kafkaAdminNotNull.describeConsumerGroups(consumerGroupsIds).all().get()
    return detailedGroups.map { (_, detailedGroup) ->
      BdtKafkaMapper.mapToConsumerGroup(detailedGroup)
    }
  }

  fun getTopics(listInternal: Boolean): List<TopicPresentable> {
    val detailedTopics = listTopicsDetailedInfo(listInternal)
    val topicNames = detailedTopics.map { it.name() }
    val loadedTopicConfig = loadTopicConfigs(topicNames)
    val internalTopics = detailedTopics.map {
      BdtKafkaMapper.mapToInternalTopic(it)
    }
    return BdtKafkaMapper.mergeWithConfigs(internalTopics, loadedTopicConfig).values.toList()
  }

  private fun loadTopicConfigs(topicNames: List<String>): Map<String, List<TopicConfig>> {
    val resources = topicNames.map {
      ConfigResource(ConfigResource.Type.TOPIC, it)
    }
    val describedConfigs = kafkaAdminNotNull.describeConfigs(resources).all().get()
    return describedConfigs.map { config ->
      val configEntry = config.value.entries()
      val internalConfigs = configEntry.map { BdtKafkaMapper.mapToInternalTopicConfig(it) }
      config.key.name() to internalConfigs

    }.toMap()
  }

  private fun listTopicsDetailedInfo(listInternal: Boolean): List<TopicDescription> {
    val listTopicsOptions = ListTopicsOptions().also {
      it.listInternal(listInternal)
    }
    val names = kafkaAdminNotNull.listTopics(listTopicsOptions).names().get()

    val describedTopics = kafkaAdminNotNull.describeTopics(names).all().get().map { it.value }
    return if (listInternal)
      describedTopics
    else
      describedTopics.filter { !it.name().startsWith("_") }
  }

  private fun getKafkaProps(connectionData: KafkaConnectionData): Properties {
    val defaultProps = listOf(
      Property(SERVER_URL, connectionData.uri.removeSuffix("/")),
      Property("connections.max.idle.ms", 10000.toString()),
      Property("default.api.timeout.ms", 5000.toString()),
      Property("request.timeout.ms", 5000.toString()),
      Property("retry.backoff.ms", 60000.toString())
    )

    val props = Properties()

    defaultProps.forEach {
      props[it.name] = it.value
    }

    PropertiesFieldComponent.parseProperties(connectionData.properties).forEach {
      props[it.name] = it.value
    }

    return props
  }

  companion object {
    private val logger = Logger.getInstance(this::class.java)
    private const val SERVER_URL = "bootstrap.servers"
  }
}