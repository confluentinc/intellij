package io.confluent.intellijplugin.core.monitoring.actions.tabs

import com.intellij.openapi.actionSystem.AnActionEvent
import io.confluent.intellijplugin.core.settings.manager.RfsConnectionDataManager
import io.confluent.intellijplugin.core.util.ConnectionUtil
import io.confluent.intellijplugin.util.KafkaMessagesBundle

class MonitoringTabDisableConnection : MonitoringTabConnectionAction() {
    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        val connectionId = e.dataContext.getData(ConnectionUtil.CONNECTION_ID)

        // Hide for Confluent Cloud tab (not a real connection)
        if (connectionId == "ccloud") {
            e.presentation.isVisible = false
            return
        }

        val selectedConnectionIds = getSelectedConnectionIds(e)
        val selectedAndEnabledNodes = selectedConnectionIds.filter {
            RfsConnectionDataManager.instance?.getConnectionById(project, it)?.isEnabled == true
        }

        e.presentation.isEnabledAndVisible = selectedAndEnabledNodes.isNotEmpty()
        if (e.presentation.isEnabledAndVisible) {
            e.presentation.text = KafkaMessagesBundle.message(
                "rfs.action.disable.connection",
                if (selectedAndEnabledNodes.size == 1) 0 else 1
            )
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedConnectionIds = getSelectedConnectionIds(e)
        if (selectedConnectionIds.isEmpty()) return
        ConnectionUtil.disableConnectionsByIds(project, selectedConnectionIds)
    }
}