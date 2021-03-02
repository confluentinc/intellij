package com.jetbrains.bigdatatools.kafka.toolwindow.config

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.jetbrains.bigdatatools.connection.updater.IntervalUpdateSettings
import com.jetbrains.bigdatatools.settings.ColumnVisibilitySettings


@State(name = "KafkaSettings", storages = [Storage(file = "kafka.xml")])
class KafkaToolWindowSettings : PersistentStateComponent<KafkaToolWindowSettings>, IntervalUpdateSettings {
  private val topicTableColumns = mutableListOf("name", "internal", "replicas", "partitionCount", "inSyncReplicas", "replicationFactor",
                                                "underReplicatedPartitions")
  val topicColumnSettings = ColumnVisibilitySettings(topicTableColumns)

  var selectedConnectionId: String? = null
  val configs: MutableMap<String, KafkaClusterConfig> = mutableMapOf()

  override var dataUpdateIntervalMillis: Int = 30000

  override fun getState(): KafkaToolWindowSettings {
    return this
  }

  override fun loadState(state: KafkaToolWindowSettings) {
    XmlSerializerUtil.copyBean(state, this)
  }

  companion object {
    fun getInstance(): KafkaToolWindowSettings = ServiceManager.getService(KafkaToolWindowSettings::class.java)
  }
}