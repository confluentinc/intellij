package io.confluent.intellijplugin.toolwindow.kafka

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import io.confluent.intellijplugin.ccloud.auth.CCloudAuthService
import io.confluent.intellijplugin.core.constants.BdtConnectionType
import io.confluent.intellijplugin.core.constants.BdtPlugins
import io.confluent.intellijplugin.core.monitoring.toolwindow.MonitoringToolWindowFactory
import io.confluent.intellijplugin.core.settings.manager.RfsConnectionDataManager
import io.confluent.intellijplugin.util.KafkaMessagesBundle

class KafkaToolWindowFactory : MonitoringToolWindowFactory() {
    override val toolWindowId = KafkaMonitoringToolWindowController.TOOL_WINDOW_ID
    override val connectionType = BdtConnectionType.KAFKA
    override val title: String = KafkaMessagesBundle.message("toolwindow.title")

    override fun shouldBeAvailable(project: Project): Boolean {
        // We will show ToolWindow stripe button if:
        // 1. Only separate Kafka plugin installed
        // 2. Full BDT installed and we have any Kafka connection configured
        // 3. User is signed in to Confluent Cloud
        val hasDirectConnections = !RfsConnectionDataManager.instance
            ?.getConnectionsByGroupId(connectionType.id, project).isNullOrEmpty()
        val isCCloudSignedIn = CCloudAuthService.getInstance().isSignedIn()

        return (BdtPlugins.isKafkaPluginInstalled() && !BdtPlugins.isFullPluginInstalled()) ||
                (BdtPlugins.isFullPluginInstalled() && hasDirectConnections) ||
                isCCloudSignedIn
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        KafkaMonitoringToolWindowController.getInstance(project)?.setUp(toolWindow)
    }
}