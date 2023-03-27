package com.jetbrains.bigdatatools.kafka.toolwindow.config

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.jetbrains.bigdatatools.common.connection.updater.IntervalUpdateSettings
import com.jetbrains.bigdatatools.common.settings.ColumnVisibilitySettings
import com.jetbrains.bigdatatools.glue.monitoring.models.GlueSchemaInfo
import com.jetbrains.bigdatatools.glue.monitoring.models.GlueSchemaVersionInfo
import com.jetbrains.bigdatatools.kafka.model.SchemaRegistryFieldsInfo
import com.jetbrains.bigdatatools.kafka.registry.confluent.ConfluentSchemaInfo

@State(name = "KafkaSettings", storages = [Storage("kafka.xml")])
class KafkaToolWindowSettings : PersistentStateComponent<KafkaToolWindowSettings>, IntervalUpdateSettings {
  var registryShowDeletedSubjects: Boolean = false
  var showFullTopicConfig: Boolean = false
  override var selectedConnectionId: String? = null

  private val topicConfigsTableColumns = mutableListOf("name", "value", "defaultValue")
  val topicConfigsColumnSettings = ColumnVisibilitySettings(topicConfigsTableColumns)

  private val topicPartitionsTableColumns = mutableListOf("partitionId",
                                                          "leader",
                                                          "offsetMin",
                                                          "offsetMax",
                                                          "inSyncReplicasCount",
                                                          "replicasCount",
                                                          "segmentCount",
                                                          "segmentSize")

  val topicPartitionsColumnSettings = ColumnVisibilitySettings(topicPartitionsTableColumns)

  private val topicTableColumns = mutableListOf("name", "replicas", "partitions",
                                                "inSyncReplicas", "replicationFactor", "underReplicatedPartitions")
  val topicColumnSettings = ColumnVisibilitySettings(topicTableColumns)

  var showInternalTopics: Boolean = false

  private val consumerGroupsTableColumns = mutableListOf("consumerGroup", "state", "consumers", "topics", "partitions")
  val consumerGroupsColumnSettings = ColumnVisibilitySettings(consumerGroupsTableColumns)

  private val schemaRegistryTableColumns = mutableListOf(ConfluentSchemaInfo::name.name,
                                                         ConfluentSchemaInfo::type.name,
                                                         ConfluentSchemaInfo::version.name)
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
    ConfluentSchemaInfo::version.name,
    ConfluentSchemaInfo::schema.name)

  val schemaRegistryVersionsTableColumnsSettings = ColumnVisibilitySettings(schemaRegistryVersionsTableColumns)

  override val configs: MutableMap<String, KafkaClusterConfig> = mutableMapOf()

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
    fun getInstance(): KafkaToolWindowSettings = ApplicationManager.getApplication().getService(KafkaToolWindowSettings::class.java)
  }
}