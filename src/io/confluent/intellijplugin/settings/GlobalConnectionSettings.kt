package io.confluent.intellijplugin.core.settings

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import io.confluent.intellijplugin.core.util.invokeLater
import io.confluent.intellijplugin.settings.KafkaUIUtils
import io.confluent.intellijplugin.util.KafkaMessagesBundle

@State(
    name = "ConfluentIntellijKafkaGlobalSettings",
    useLoadedStateAsExisting = false, // This is hack, needed because we need to transfer sensitive data from settings to PasswordSafe
    storages = [
        Storage("confluent_kafka_settings.xml")
    ]
)
@Service
class GlobalConnectionSettings : ConnectionSettingsBase() {
    companion object {
        private val logger = Logger.getInstance(GlobalConnectionSettings::class.java)
        fun getInstance(): GlobalConnectionSettings = service()
    }

    init {
        ApplicationManager.getApplication().messageBus.connect(this).subscribe(
            DynamicPluginListener.TOPIC,
            AdditionalPluginLoadingListener()
        )
    }

    override fun loadState(state: ConnectionPersistentState) {
        logger.warn("GlobalConnectionSettings: Loading state with ${state.connections.size} connections, legacyMigrationCompleted=${state.legacyMigrationCompleted}")
        super.loadState(state)

        // Only attempt migration if not already completed
        if (!legacyMigrationCompleted) {
            migrateLegacyConnections()
        } else {
            logger.warn("GlobalConnectionSettings: Legacy migration already completed, skipping")
        }
    }

    /**
     * Called when there's no existing state file (confluent_kafka_settings.xml doesn't exist).
     * This is where we check for and migrate legacy connections.
     */
    override fun noStateLoaded() {
        logger.warn("GlobalConnectionSettings: No state file found, checking for legacy connections...")
        super.noStateLoaded()
        migrateLegacyConnections()
    }

    private fun migrateLegacyConnections() {
        try {
            val legacySettings = LegacyGlobalConnectionSettings.getInstance()

            if (legacySettings.isMigrationNeeded()) {
                val legacyConnections = legacySettings.getLegacyConnections()
                logger.warn("GlobalConnectionSettings: Migrating ${legacyConnections.size} legacy connections from BigDataIdeGlobalConnectionSettings")

                val existingIds = getConnections().map { it.innerId }.toSet()
                var migratedCount = 0

                legacyConnections.forEach { legacyConn ->
                    if (legacyConn.innerId !in existingIds) {
                        logger.warn("[MIGRATING] innerId=${legacyConn.innerId}, name=${legacyConn.name}, groupId=${legacyConn.groupId}")
                        addConnection(unpackData(legacyConn))
                        migratedCount++
                    } else {
                        logger.warn("[SKIPPING-DUPLICATE] innerId=${legacyConn.innerId}, name=${legacyConn.name} - already exists")
                    }
                }

                logger.warn("GlobalConnectionSettings: Migration complete. Migrated $migratedCount connections, skipped ${legacyConnections.size - migratedCount} duplicates")
                legacySettings.markMigrationComplete()

                // Mark migration as complete, this will be persisted when getState() is called
                legacyMigrationCompleted = true

                // Show notification to user about migration result
                showMigrationNotification(migratedCount)
                migrationNotificationShown = true
            } else {
                logger.warn("GlobalConnectionSettings: No legacy settings file found")
                // Don't mark migration complete, user might import old settings later
                // But show notification once with workaround info
                if (!migrationNotificationShown) {
                    showNoLegacyNotification()
                    migrationNotificationShown = true
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to migrate legacy global connections", e)
        }
    }

    private fun showMigrationNotification(migratedCount: Int) {
        logger.warn("GlobalConnectionSettings: Scheduling migration notification for $migratedCount connections")
        invokeLater {
            logger.warn("GlobalConnectionSettings: Inside invokeLater, creating notification")
            try {
                val notificationGroup = NotificationGroupManager.getInstance()
                    .getNotificationGroup("kafka")
                logger.warn("GlobalConnectionSettings: Got notification group: $notificationGroup")

                val notification = notificationGroup.createNotification(
                    KafkaMessagesBundle.message("migration.notification.title"),
                    KafkaMessagesBundle.message("migration.notification.content", migratedCount),
                    NotificationType.INFORMATION
                )

                notification.addAction(
                    NotificationAction.create(KafkaMessagesBundle.message("migration.notification.multiversion.title")) { _, _ ->
                        KafkaUIUtils.showMultiVersionInfoDialog()
                        notification.expire()
                    }
                )

                // Show notification in all open projects, or without project context if none are open
                val openProjects = ProjectManager.getInstance().openProjects
                logger.warn("GlobalConnectionSettings: Open projects count: ${openProjects.size}")
                if (openProjects.isNotEmpty()) {
                    openProjects.forEach { project ->
                        if (!project.isDisposed) {
                            logger.warn("GlobalConnectionSettings: Showing notification in project: ${project.name}")
                            notification.notify(project)
                        }
                    }
                } else {
                    logger.warn("GlobalConnectionSettings: No open projects, showing notification without project context")
                    notification.notify(null)
                }
                logger.warn("GlobalConnectionSettings: Migration notification displayed successfully")
            } catch (e: Exception) {
                logger.error("GlobalConnectionSettings: Failed to show migration notification", e)
            }
        }
    }

    private fun showNoLegacyNotification() {
        logger.warn("GlobalConnectionSettings: Scheduling no-legacy notification")
        invokeLater {
            logger.warn("GlobalConnectionSettings: Inside invokeLater, creating no-legacy notification")
            try {
                val notificationGroup = NotificationGroupManager.getInstance()
                    .getNotificationGroup("kafka")

                val notification = notificationGroup.createNotification(
                    KafkaMessagesBundle.message("migration.notification.no.legacy.title"),
                    KafkaMessagesBundle.message("migration.notification.no.legacy.content"),
                    NotificationType.INFORMATION
                )

                notification.addAction(
                    NotificationAction.create(KafkaMessagesBundle.message("migration.notification.multiversion.title")) { _, _ ->
                        KafkaUIUtils.showMultiVersionInfoDialog()
                        notification.expire()
                    }
                )

                // Show notification in all open projects, or without project context if none are open
                val openProjects = ProjectManager.getInstance().openProjects
                if (openProjects.isNotEmpty()) {
                    openProjects.forEach { project ->
                        if (!project.isDisposed) {
                            notification.notify(project)
                        }
                    }
                } else {
                    notification.notify(null)
                }
                logger.warn("GlobalConnectionSettings: No-legacy notification displayed successfully")
            } catch (e: Exception) {
                logger.error("GlobalConnectionSettings: Failed to show no-legacy notification", e)
            }
        }
    }
}
