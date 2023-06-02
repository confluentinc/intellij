package com.jetbrains.bigdatatools.kafka.toolwindow.config

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.XmlSerializerUtil
import com.jetbrains.bigdatatools.common.connection.updater.IntervalUpdateSettings
import com.jetbrains.bigdatatools.common.settings.ColumnVisibilitySettings
import com.jetbrains.bigdatatools.kafka.model.BdtTopicPartition
import com.jetbrains.bigdatatools.kafka.model.ConsumerGroupPresentable
import com.jetbrains.bigdatatools.kafka.model.TopicPresentable
import com.jetbrains.bigdatatools.kafka.registry.common.KafkaSchemaInfo

@State(name = "KafkaSettings", storages = [Storage("kafka.xml")])
class KafkaToolWindowSettings : PersistentStateComponent<KafkaToolWindowSettings>, IntervalUpdateSettings {
  var showFullTopicConfig: Boolean = false
  override var selectedConnectionId: String? = null

  private val topicConfigsTableColumns = mutableListOf("name", "value", "defaultValue")
  val topicConfigsColumnSettings = ColumnVisibilitySettings(topicConfigsTableColumns)

  private val topicPartitionsTableColumns = mutableListOf(BdtTopicPartition::partitionId.name,
                                                          BdtTopicPartition::leader.name,
                                                          BdtTopicPartition::replicas.name,
                                                          BdtTopicPartition::offsets.name)

  val topicPartitionsColumnSettings = ColumnVisibilitySettings(topicPartitionsTableColumns)

  private val topicTableColumns = mutableListOf(
    TopicPresentable::name.name,
    TopicPresentable::messageCount.name,
    TopicPresentable::partitions.name,
    TopicPresentable::replicationFactor.name,
    TopicPresentable::inSyncReplicas.name)
  val topicColumnSettings = ColumnVisibilitySettings(topicTableColumns)

  var showInternalTopics: Boolean = false

  private val consumerGroupsTableColumns = mutableListOf(
    ConsumerGroupPresentable::consumerGroup.name,
    ConsumerGroupPresentable::state.name,
    ConsumerGroupPresentable::consumers.name,
    ConsumerGroupPresentable::topics.name,
    ConsumerGroupPresentable::partitions.name)
  val consumerGroupsColumnSettings = ColumnVisibilitySettings(consumerGroupsTableColumns)

  private val confluentSchemaTableColumns = mutableListOf(KafkaSchemaInfo::name.name,
                                                          KafkaSchemaInfo::type.name,
                                                          KafkaSchemaInfo::version.name)

  val confluentSchemaTableColumnSettings = ColumnVisibilitySettings(confluentSchemaTableColumns)

  private val glueSchemaTableColumns = confluentSchemaTableColumns + mutableListOf(
    KafkaSchemaInfo::compatibility.name,
    KafkaSchemaInfo::updatedTime.name,
    KafkaSchemaInfo::description.name,
  )

  val glueSchemaTableColumnSettings = ColumnVisibilitySettings(glueSchemaTableColumns.toMutableList())


  override val configs: MutableMap<String, KafkaClusterConfig> = mutableMapOf()

  override var dataUpdateIntervalMillis: Int = 0

  fun getOrCreateConfig(connectionId: String): KafkaClusterConfig {
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
    fun getInstance(): KafkaToolWindowSettings = service()
  }
}