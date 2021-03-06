package com.jetbrains.bigdatatools.kafka.toolwindow.config

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.jetbrains.bigdatatools.connection.updater.IntervalUpdateSettings
import com.jetbrains.bigdatatools.kafka.model.ConsumerGroupPresentable
import com.jetbrains.bigdatatools.settings.ColumnVisibilitySettings


@State(name = "KafkaSettings", storages = [Storage(file = "kafka.xml")])
class KafkaToolWindowSettings : PersistentStateComponent<KafkaToolWindowSettings>, IntervalUpdateSettings {
  var selectedConnectionId: String? = null

  private val topicConfigsTableColumns = mutableListOf("name", "value")
  val topicConfigsColumnSettings = ColumnVisibilitySettings(topicConfigsTableColumns)


  private val topicPartitionsTableColumns = mutableListOf("partition",
                                                          "leader",
                                                          "offsetMin",
                                                          "offsetMax",
                                                          "inSyncReplicasCount",
                                                          "replicasCount",
                                                          "segmentCount",
                                                          "segmentSize")
  val topicPartitionsColumnSettings = ColumnVisibilitySettings(topicPartitionsTableColumns)

  private val topicTableColumns = mutableListOf("name", "internal", "replicas", "partitionCount",
                                                "inSyncReplicas", "replicationFactor", "underReplicatedPartitions")
  val topicColumnSettings = ColumnVisibilitySettings(topicTableColumns)

  var showInternalTopics: Boolean = false

  private val consumerGroupsTableColumns = ConsumerGroupPresentable.renderableColumns.map { it.name }.toMutableList()
  val consumerGroupsColumnSettings = ColumnVisibilitySettings(consumerGroupsTableColumns)

  val configs: MutableMap<String, KafkaClusterConfig> = mutableMapOf()

  override var dataUpdateIntervalMillis: Int = 30000

  fun setSelectedTopicName(connectionId: String, selectedTopic: String) {
    getOrCreateSparkConfig(connectionId).selectedTopic = selectedTopic
  }

  private fun getOrCreateSparkConfig(connectionId: String): KafkaClusterConfig {
    var config = configs[connectionId]
    if (config == null) {
      config = KafkaClusterConfig()
      configs[connectionId] = config
    }
    return config
  }


  override fun getState(): KafkaToolWindowSettings = this

  override fun loadState(state: KafkaToolWindowSettings) {
    XmlSerializerUtil.copyBean(state, this)
  }

  companion object {
    fun getInstance(): KafkaToolWindowSettings = ServiceManager.getService(KafkaToolWindowSettings::class.java)
  }
}