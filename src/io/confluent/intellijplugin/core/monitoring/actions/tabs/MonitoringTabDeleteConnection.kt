package io.confluent.intellijplugin.core.monitoring.actions.tabs

import com.intellij.CommonBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.IconLoader
import io.confluent.intellijplugin.core.util.ConnectionUtil
import io.confluent.intellijplugin.settings.app.KafkaPluginSettings
import io.confluent.intellijplugin.toolwindow.KafkaMonitoringToolWindowController
import io.confluent.intellijplugin.util.KafkaMessagesBundle

private val HideCCloudTab = IconLoader.getIcon(
    "/icons/hide.svg",
    MonitoringTabDeleteConnection::class.java
)

class MonitoringTabDeleteConnection : MonitoringTabConnectionAction() {
    override fun update(e: AnActionEvent) {
        val connectionId = e.dataContext.getData(ConnectionUtil.CONNECTION_ID)

        if (connectionId == "ccloud") {
            e.presentation.text = KafkaMessagesBundle.message("action.hideConfluentCloudTab.text")
            e.presentation.icon = HideCCloudTab
            e.presentation.isEnabled = true
            return
        }

        val selectedConnectionIds = getSelectedConnectionIds(e)
        e.presentation.isEnabled = selectedConnectionIds.isNotEmpty()
        e.presentation.text =
            KafkaMessagesBundle.message("action.deleteConnection.text", if (selectedConnectionIds.size == 1) 0 else 1)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val connectionId = e.dataContext.getData(ConnectionUtil.CONNECTION_ID)

        if (connectionId == "ccloud") {
            val confirmed = Messages.showOkCancelDialog(
                project,
                KafkaMessagesBundle.message("action.hideConfluentCloudTab.confirm.message"),
                "",
                KafkaMessagesBundle.message("action.hideConfluentCloudTab.text"),
                CommonBundle.getCancelButtonText(),
                Messages.getQuestionIcon()
            )
            if (confirmed != Messages.OK) return

            KafkaPluginSettings.getInstance().hideConfluentCloudTab = true
            for (p in ProjectManager.getInstance().openProjects) {
                p.getService(KafkaMonitoringToolWindowController::class.java)?.removeConfluentCloudTab()
            }
            return
        }

        val selectedConnectionIds = getSelectedConnectionIds(e)
        if (selectedConnectionIds.isEmpty()) return
        ConnectionUtil.removeConnectionsWithConfirmation(project, selectedConnectionIds)
    }
}
