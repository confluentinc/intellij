package com.jetbrains.bigdatatools.kafka.toolwindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.monitoring.toolwindow.ToolWindowActivator
import com.jetbrains.bigdatatools.settings.manager.RfsConnectionDataManager

class KafkaToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    KafkaMonitoringToolWindowController.getInstance(project)?.setUp(toolWindow)
  }

  override fun init(toolWindow: ToolWindow) {
    toolWindow.stripeTitle = KafkaMessagesBundle.message("toolwindow.title")

    if (!toolWindow.isAvailable) {
      ToolWindowActivator(KafkaMonitoringToolWindowController.TOOL_WINDOW_ID, KafkaConnectionData::class.java)
    }
  }

  override fun shouldBeAvailable(project: Project): Boolean {
    return !RfsConnectionDataManager.instance?.getTyped<KafkaConnectionData>(project).isNullOrEmpty()
  }
}