package io.confluent.intellijplugin.toolwindow.kafka.actions

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnActionEvent
import io.confluent.intellijplugin.ccloud.auth.CCloudAuthService
import io.confluent.intellijplugin.core.monitoring.actions.tabs.MonitoringTabConnectionAction
import io.confluent.intellijplugin.core.util.ConnectionUtil
import io.confluent.intellijplugin.toolwindow.kafka.KafkaMonitoringToolWindowController

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
        val project = e.project ?: return
        val connectionId = e.dataContext.getData(ConnectionUtil.CONNECTION_ID)

        if (connectionId != "ccloud") return

        // Sign out from auth service
        CCloudAuthService.getInstance().signOut()

        // Get the Confluent Cloud tab controller and tell it to sign out
        val tabController = KafkaMonitoringToolWindowController.getInstance(project)
            ?.getConfluentCloudTabController()

        tabController?.signOut()

        Notifications.Bus.notify(
            Notification(
                "Kafka Notification",
                "Signed out from Confluent Cloud",
                "",
                NotificationType.INFORMATION
            ),
            project
        )
    }
}
