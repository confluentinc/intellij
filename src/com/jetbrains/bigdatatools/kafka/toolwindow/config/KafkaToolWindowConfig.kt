package com.jetbrains.bigdatatools.kafka.toolwindow.config

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.jetbrains.bigdatatools.connection.updater.IntervalUpdateSettings


@State(name = "KafkaSettings", storages = [Storage(file = "kafka.xml")])
class KafkaToolWindowConfig : PersistentStateComponent<KafkaToolWindowConfig>, IntervalUpdateSettings {

  //
  //var applicationColumns = arrayListOf("id", "user", "name", "applicationType", "queue", "state", "finalStatus", "progress", "trackingUrl",
  //                                     "startedTime", "elapsedTime")
  //var applicationDetailsColumns = AppInfo.renderableColumns.map { it.name }.toMutableList()
  //
  //var nodesColumns = arrayListOf("rack", "state", "id", "nodeHostName", "nodeHTTPAddress", "lastHealthUpdate", "numContainers",
  //                               "usedMemoryMB", "availMemoryMB", "usedVirtualCores", "availableVirtualCores", "version")
  //var nodeLabelColumns = arrayListOf("labelName", "labelType", "numActiveNodeMangers", "totalResource")
  //var appAttemptColumns = arrayListOf("id", "startTime", "finishedTime", "containerId")
  //var containerInfoColumns = arrayListOf("containerId", "nodeId", "containerExitStatus", "logUrl")
  //
  //val applicationColumnSettings = ColumnVisibilitySettings(applicationColumns)
  //val applicationDetailsColumnSettings = ColumnVisibilitySettings(applicationDetailsColumns)
  //val nodeColumnSettings = ColumnVisibilitySettings(nodesColumns)
  //val nodeLabelColumnSettings = ColumnVisibilitySettings(nodeLabelColumns)
  //val appAttemptColumnSettings = ColumnVisibilitySettings(appAttemptColumns)
  //val containerInfoColumnSettings = ColumnVisibilitySettings(containerInfoColumns)
  //
  //override var dataUpdateIntervalMillis = 30000
  //
  //var configs = HashMap<String, HadoopConfig>()
  //
  //var nodeStates = HashSet<NodeState>().apply { addAll(NodeState.values()) }
  //var applicationStates = HashSet<YarnApplicationState>().apply { addAll(YarnApplicationState.values()) }

  var selectedConnectionId: String? = null
  val configs: MutableMap<String, KafkaClusterConfig> = mutableMapOf()

  override var dataUpdateIntervalMillis: Int = 30000

  override fun getState(): KafkaToolWindowConfig {
    return this
  }

  override fun loadState(state: KafkaToolWindowConfig) {
    XmlSerializerUtil.copyBean(state, this)
  }

  fun setTopicsSplitterProportion(connectionId: String, proportion: Float) {
    TODO("Not yet implemented")
  }

  companion object {
    fun getInstance(): KafkaToolWindowConfig = ServiceManager.getService(KafkaToolWindowConfig::class.java)
  }
}