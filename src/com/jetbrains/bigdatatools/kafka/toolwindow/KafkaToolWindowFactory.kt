package com.jetbrains.bigdatatools.kafka.toolwindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

class KafkaToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    KafkaMonitoringToolWindowController.getInstance(project)?.setUp(toolWindow)
  }

  override fun init(toolWindow: ToolWindow) {
    toolWindow.stripeTitle = "Kafka"
  }
}