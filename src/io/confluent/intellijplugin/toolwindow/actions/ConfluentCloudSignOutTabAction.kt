package io.confluent.intellijplugin.toolwindow.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import io.confluent.intellijplugin.ccloud.auth.CCloudAuthService
import io.confluent.intellijplugin.core.monitoring.actions.tabs.MonitoringTabConnectionAction
import io.confluent.intellijplugin.core.util.ConnectionUtil

/**
 * Action to sign out from Confluent Cloud.
 * Only visible in the Confluent Cloud tab's 3-dot menu.
 */
class ConfluentCloudSignOutTabAction : MonitoringTabConnectionAction() {
    override fun update(e: AnActionEvent) {
        val connectionId = e.dataContext.getData(ConnectionUtil.CONNECTION_ID)

        // Only show this action for the Confluent Cloud tab
        e.presentation.isVisible = connectionId == "ccloud" && CCloudAuthService.getInstance().isSignedIn()
        e.presentation.isEnabled = e.presentation.isVisible
    }

    override fun actionPerformed(e: AnActionEvent) {
        val connectionId = e.dataContext.getData(ConnectionUtil.CONNECTION_ID)
        if (connectionId != "ccloud") return

        CCloudAuthService.getInstance().signOut()
    }
}
