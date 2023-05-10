package com.jetbrains.bigdatatools.kafka.toolwindow.config

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.XmlSerializerUtil
import com.jetbrains.bigdatatools.common.connection.updater.IntervalUpdateSettings
import com.jetbrains.bigdatatools.common.settings.ColumnVisibilitySettings
import com.jetbrains.bigdatatools.kafka.model.ConsumerGroupPresentable
import com.jetbrains.bigdatatools.kafka.model.SchemaRegistryFieldsInfo
import com.jetbrains.bigdatatools.kafka.model.TopicPartition
import com.jetbrains.bigdatatools.kafka.model.TopicPresentable
import com.jetbrains.bigdatatools.kafka.registry.confluent.ConfluentSchemaInfo
import com.jetbrains.bigdatatools.kafka.registry.glue.models.GlueSchemaInfo
import com.jetbrains.bigdatatools.kafka.registry.glue.models.GlueSchemaVersionInfo

@State(name = "KafkaSettings", storages = [Storage("kafka.xml")])
class KafkaToolWindowSettings : PersistentStateComponent<KafkaToolWindowSettings>, IntervalUpdateSettings {
  var registryShowDeletedSubjects: Boolean = false
  var showFullTopicConfig: Boolean = false
  override var selectedConnectionId: String? = null

  private val topicConfigsTableColumns = mutableListOf("name", "value", "defaultValue")
  val topicConfigsColumnSettings = ColumnVisibilitySettings(topicConfigsTableColumns)

  private val topicPartitionsTableColumns = mutableListOf(TopicPartition::partitionId.name,
                                                          TopicPartition::leader.name,
                                                          TopicPartition::replicas.name)

  val topicPartitionsColumnSettings = ColumnVisibilitySettings(topicPartitionsTableColumns)

  private val topicTableColumns = mutableListOf(
    TopicPresentable::name.name,
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

  private val schemaRegistryTableColumns = mutableListOf(ConfluentSchemaInfo::name.name,
                                                         ConfluentSchemaInfo::type.name,
                                                         ConfluentSchemaInfo::versions.name)
  val schemaRegistryTableColumnSettings = ColumnVisibilitySettings(schemaRegistryTableColumns)

  private val glueSchemaTableColumns = mutableListOf(GlueSchemaInfo::schemaName.name,
                                                     GlueSchemaInfo::registryName.name,
                                                     GlueSchemaInfo::description.name,
                                                     GlueSchemaInfo::createdTime.name,
                                                     GlueSchemaInfo::updatedTime.name)
  val glueSchemaTableColumnSettings = ColumnVisibilitySettings(glueSchemaTableColumns)

  private val schemaRegistryFieldsTableColumns = mutableListOf(
    SchemaRegistryFieldsInfo::name.name,
    SchemaRegistryFieldsInfo::type.name,
    SchemaRegistryFieldsInfo::default.name)
  val schemaRegistryFieldsTableColumnSettings = ColumnVisibilitySettings(schemaRegistryFieldsTableColumns)

  private val glueSchemaVersionsTableColumns = mutableListOf(
    GlueSchemaVersionInfo::version.name,
    GlueSchemaVersionInfo::registered.name,
    GlueSchemaVersionInfo::status.name)

  val glueSchemaVersionsTableColumnsSettings = ColumnVisibilitySettings(glueSchemaVersionsTableColumns)

  private val schemaRegistryVersionsTableColumns = mutableListOf(
    ConfluentSchemaInfo::id.name,
    ConfluentSchemaInfo::versions.name)

  val schemaRegistryVersionsTableColumnsSettings = ColumnVisibilitySettings(schemaRegistryVersionsTableColumns)

  override val configs: MutableMap<String, KafkaClusterConfig> = mutableMapOf()

  override var dataUpdateIntervalMillis: Int = 60_000

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