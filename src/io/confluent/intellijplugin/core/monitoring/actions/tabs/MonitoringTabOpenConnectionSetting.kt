package io.confluent.intellijplugin.core.monitoring.actions.tabs

import com.intellij.openapi.actionSystem.AnActionEvent
import io.confluent.intellijplugin.core.settings.ConnectionSettings
import io.confluent.intellijplugin.core.util.ConnectionUtil

class MonitoringTabOpenConnectionSetting : MonitoringTabConnectionAction() {
    override fun update(e: AnActionEvent) {
        super.update(e)
        val connectionId = e.dataContext.getData(ConnectionUtil.CONNECTION_ID)
        // Hide for Confluent Cloud tab (not a real connection)
        e.presentation.isVisible = connectionId != "ccloud"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedConnectionIds = getSelectedConnectionIds(e)
        if (selectedConnectionIds.isEmpty()) return
        ConnectionSettings.open(project, selectedConnectionIds.first())
    }
}


