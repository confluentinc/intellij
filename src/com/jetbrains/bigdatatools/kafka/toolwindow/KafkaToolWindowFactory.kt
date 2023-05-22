package com.jetbrains.bigdatatools.kafka.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.jetbrains.bigdatatools.common.constants.BdtConnectionType
import com.jetbrains.bigdatatools.common.constants.BdtPlugins
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.MonitoringToolWindowFactory
import com.jetbrains.bigdatatools.common.settings.manager.RfsConnectionDataManager
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

class KafkaToolWindowFactory : MonitoringToolWindowFactory() {
  override val toolWindowId = KafkaMonitoringToolWindowController.TOOL_WINDOW_ID
  override val connectionType = BdtConnectionType.KAFKA
  override val title: String = KafkaMessagesBundle.message("toolwindow.title")

  override fun shouldBeAvailable(project: Project) =
    (BdtPlugins.isKafkaPluginInstalled()) ||
    (BdtPlugins.isFullPluginInstalled() &&
     !RfsConnectionDataManager.instance?.getConnectionsByGroupId(connectionType.id, project)?.filter { it.isEnabled }.isNullOrEmpty())

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    KafkaMonitoringToolWindowController.getInstance(project)?.setUp(toolWindow)
  }
}