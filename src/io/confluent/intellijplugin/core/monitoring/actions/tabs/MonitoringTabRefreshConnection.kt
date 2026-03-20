package io.confluent.intellijplugin.core.monitoring.actions.tabs

import com.intellij.openapi.actionSystem.AnActionEvent
import io.confluent.intellijplugin.core.monitoring.toolwindow.MainTreeController.Companion.rfsPath
import io.confluent.intellijplugin.core.settings.manager.RfsConnectionDataManager
import io.confluent.intellijplugin.core.util.ConnectionUtil
import io.confluent.intellijplugin.util.KafkaMessagesBundle

class MonitoringTabRefreshConnection : MonitoringTabConnectionAction() {
    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedConnectionIds = getSelectedConnectionIds(e)
        val selectedAndEnabledNodes = selectedConnectionIds.filter {
            RfsConnectionDataManager.instance?.getConnectionById(project, it)?.isEnabled == true
        }
        e.presentation.isEnabledAndVisible = selectedAndEnabledNodes.isNotEmpty()
        if (e.presentation.isVisible) {
            e.presentation.text = KafkaMessagesBundle.message(
                "action.refreshConnection.text",
                if (selectedConnectionIds.size == 1) 0 else 1
            )
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedConnectionIds = getSelectedConnectionIds(e)
        if (selectedConnectionIds.isEmpty()) return
        ConnectionUtil.refreshConnectionsByIds(project, selectedConnectionIds, e.rfsPath)
    }
}