package io.confluent.intellijplugin.toolwindow.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import io.confluent.intellijplugin.ccloud.auth.CCloudAuthService
import io.confluent.intellijplugin.core.monitoring.actions.tabs.MonitoringTabConnectionAction
import io.confluent.intellijplugin.core.util.ConnectionUtil
import io.confluent.intellijplugin.util.KafkaMessagesBundle

/**
 * Sign-in / sign-out toggle for the Confluent Cloud tab's 3-dot menu.
 * Shows "Sign in" when not authenticated, "Sign out" when authenticated.
 */
class ConfluentCloudTabAction : MonitoringTabConnectionAction() {
    override fun update(e: AnActionEvent) {
        val connectionId = e.dataContext.getData(ConnectionUtil.CONNECTION_ID)

        e.presentation.isVisible = connectionId == "ccloud"
        e.presentation.isEnabled = e.presentation.isVisible

        if (CCloudAuthService.getInstance().isSignedIn()) {
            e.presentation.text = KafkaMessagesBundle.message("confluent.cloud.settings.sign.out")
            e.presentation.icon = AllIcons.Actions.Exit
        } else {
            e.presentation.text = KafkaMessagesBundle.message("confluent.cloud.welcome.panel.cta")
            e.presentation.icon = AllIcons.General.User
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val connectionId = e.dataContext.getData(ConnectionUtil.CONNECTION_ID)
        if (connectionId != "ccloud") return

        val authService = CCloudAuthService.getInstance()
        if (authService.isSignedIn()) {
            authService.signOut(invokedPlace = "tool_window_action")
        } else {
            authService.signIn("tool_window_action")
        }
    }
}
