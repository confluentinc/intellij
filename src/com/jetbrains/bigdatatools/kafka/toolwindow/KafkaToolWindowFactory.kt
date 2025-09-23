package io.confluent.kafka.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import io.confluent.kafka.core.constants.BdtConnectionType
import io.confluent.kafka.core.constants.BdtPlugins
import io.confluent.kafka.core.monitoring.toolwindow.MonitoringToolWindowFactory
import io.confluent.kafka.core.settings.manager.RfsConnectionDataManager
import io.confluent.kafka.util.KafkaMessagesBundle

class KafkaToolWindowFactory : MonitoringToolWindowFactory() {
  override val toolWindowId = KafkaMonitoringToolWindowController.TOOL_WINDOW_ID
  override val connectionType = BdtConnectionType.KAFKA
  override val title: String = KafkaMessagesBundle.message("toolwindow.title")

  override fun shouldBeAvailable(project: Project): Boolean {
    // We will show ToolWindow stripe button if:
    // 1. Only separate Kafka plugin installed
    // 2. Full BDT installed and we have any Kafka connection configured.
    return (BdtPlugins.isKafkaPluginInstalled() && !BdtPlugins.isFullPluginInstalled()) ||
           (BdtPlugins.isFullPluginInstalled() &&
            !RfsConnectionDataManager.instance?.getConnectionsByGroupId(connectionType.id, project).isNullOrEmpty())
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    KafkaMonitoringToolWindowController.getInstance(project)?.setUp(toolWindow)
  }
}