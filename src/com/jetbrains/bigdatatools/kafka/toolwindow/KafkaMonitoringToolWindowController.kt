package com.jetbrains.bigdatatools.kafka.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.kafka.settings.KafkaConnectionGroup
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.kafka.toolwindow.controllers.ClusterPageController
import com.jetbrains.bigdatatools.monitoring.toolwindow.ComponentController
import com.jetbrains.bigdatatools.monitoring.toolwindow.MonitoringToolWindowController
import com.jetbrains.bigdatatools.settings.connections.ConnectionData
import com.jetbrains.bigdatatools.settings.connections.ConnectionGroup
import com.jetbrains.bigdatatools.settings.manager.RfsConnectionDataManager

class KafkaMonitoringToolWindowController(project: Project) : MonitoringToolWindowController(project) {

  override val helpTopicId: String = "big.data.tools.kafka"

  override val settings = KafkaToolWindowSettings.getInstance()

  override fun createConnectionGroup(): ConnectionGroup = KafkaConnectionGroup()

  override fun isSupportedData(connectionData: ConnectionData): Boolean = connectionData is KafkaConnectionData

  override fun createMainController(connectionData: ConnectionData): ComponentController = ClusterPageController(project,
                                                                                                                 connectionData as KafkaConnectionData)

  override fun focusOn(connectionId: String) {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return

    toolWindow.show {
      val contentManager = toolWindow.contentManager
      val content = contentManager.contents.firstOrNull { it.getUserData(CONNECTION_ID) == connectionId } ?: return@show
      contentManager.setSelectedContent(content)
      contentManager.requestFocus(content, true)
    }
  }

  override fun getEnabledConnectionSettings(): List<KafkaConnectionData> =
    RfsConnectionDataManager.instance?.getTyped<KafkaConnectionData>(project)
      ?.filter { it.isEnabled } ?: emptyList()

  override fun cleanUpSettingsForMissedConnections(connections: List<ConnectionData>) {
    val connectionIds = connections.map { it.innerId }
    val storedConfigs = settings.configs
    val deprecatedConfigIds = storedConfigs.keys.minus(connectionIds.toSet())
    storedConfigs -= deprecatedConfigIds
    storedConfigs.entries.removeIf { !connectionIds.contains(it.key) }
  }


  companion object {
    fun getInstance(project: Project): KafkaMonitoringToolWindowController? = project.getService(
      KafkaMonitoringToolWindowController::class.java)

    const val TOOL_WINDOW_ID = "KafkaToolWindow"
  }
}