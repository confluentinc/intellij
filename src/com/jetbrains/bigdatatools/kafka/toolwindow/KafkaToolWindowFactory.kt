package com.jetbrains.bigdatatools.kafka.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.jetbrains.bigdatatools.kafka.settings.KafkaSettingsIds
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.MonitoringToolWindowFactory

class KafkaToolWindowFactory : MonitoringToolWindowFactory() {
  override val controllerId = KafkaMonitoringToolWindowController.TOOL_WINDOW_ID
  override val connectionGroupId = KafkaSettingsIds.GROUP_ID
  override val title: String = KafkaMessagesBundle.message("toolwindow.title")

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    KafkaMonitoringToolWindowController.getInstance(project)?.setUp(toolWindow)
  }
}