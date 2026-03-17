package io.confluent.intellijplugin.updater

import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import io.confluent.intellijplugin.settings.app.KafkaPluginSettings
import io.confluent.intellijplugin.util.KafkaMessagesBundle

private const val DOCS_URL =
    "https://docs.confluent.io/cloud/current/client-apps/kafka-plugin-for-jetbrains-ides.html"

internal class RebrandNotificationActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val settings = KafkaPluginSettings.getInstance()
        if (settings.rebrandNotificationShown) return

        settings.rebrandNotificationShown = true

        val notificationGroup = NotificationGroupManager.getInstance()
            .getNotificationGroup("kafka")

        val notification = notificationGroup.createNotification(
            KafkaMessagesBundle.message("rebrand.notification.title"),
            KafkaMessagesBundle.message("rebrand.notification.content"),
            NotificationType.INFORMATION
        )

        notification.addAction(
            NotificationAction.createSimpleExpiring(
                KafkaMessagesBundle.message("rebrand.notification.action.docs")
            ) {
                BrowserUtil.browse(DOCS_URL)
            }
        )

        notification.notify(project)
    }
}
