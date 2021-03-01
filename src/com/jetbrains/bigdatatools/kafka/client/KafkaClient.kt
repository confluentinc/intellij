package com.jetbrains.bigdatatools.kafka.client

import com.intellij.openapi.Disposable
import com.jetbrains.bigdatatools.kafka.model.InternalTopicConfig
import com.jetbrains.bigdatatools.kafka.model.TopicPresentable
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.KafkaAdminClient
import org.apache.kafka.clients.admin.ListTopicsOptions
import org.apache.kafka.clients.admin.TopicDescription
import org.apache.kafka.common.config.ConfigResource
import java.util.*

class KafkaClient(connectionData: KafkaConnectionData) : Disposable {
  private val kafkaAdmin: AdminClient = KafkaAdminClient.create(getKafkaProps(connectionData))

  override fun dispose() {
    kafkaAdmin.close()
  }

  fun checkConnection(): Throwable? = try {
    kafkaAdmin.describeCluster().controller().get()
    null
  }
  catch (t: Throwable) {
    t
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

  private fun loadTopicConfigs(topicNames: List<String>): Map<String, List<InternalTopicConfig>> {
    val resources = topicNames.map {
      ConfigResource(ConfigResource.Type.TOPIC, it)
    }
    val describedConfigs = kafkaAdmin.describeConfigs(resources).all().get()
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
    val names = kafkaAdmin.listTopics(listTopicsOptions).names().get()

    val describedTopics = kafkaAdmin.describeTopics(names).all().get().map { it.value }
    return if (listInternal)
      describedTopics
    else
      describedTopics.filter { !it.name().startsWith("_") }
  }

  private fun getKafkaProps(connectionData: KafkaConnectionData): Properties {
    val props = Properties()
    props["bootstrap.servers"] = connectionData.uri
    props["connections.max.idle.ms"] = 10000
    props["default.api.timeout.ms"] = 5000
    props["request.timeout.ms"] = 5000
    props["retry.backoff.ms"] = 60000
    return props
  }
}