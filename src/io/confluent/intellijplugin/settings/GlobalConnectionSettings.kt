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
import com.intellij.openapi.diagnostic.thisLogger
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
        private val logger = thisLogger()
        fun getInstance(): GlobalConnectionSettings = service()
    }

    init {
        ApplicationManager.getApplication().messageBus.connect(this).subscribe(
            DynamicPluginListener.TOPIC,
            AdditionalPluginLoadingListener()
        )
    }

    override fun loadState(state: ConnectionPersistentState) {
        logger.debug("Loading state with ${state.connections.size} connections, legacyMigrationCompleted=${state.legacyMigrationCompleted}")
        super.loadState(state)

        // Only attempt migration if not already completed
        if (!legacyMigrationCompleted) {
            migrateLegacyConnections()
        } else {
            logger.debug("Legacy migration already completed, skipping")
        }
    }

    /**
     * Called when there's no existing state file (confluent_kafka_settings.xml doesn't exist).
     * This is where we check for and migrate legacy connections.
     */
    override fun noStateLoaded() {
        logger.debug("No state file found, checking for legacy connections...")
        super.noStateLoaded()
        migrateLegacyConnections()
    }

    private fun migrateLegacyConnections() {
        try {
            val legacySettings = LegacyGlobalConnectionSettings.getInstance()

            if (legacySettings.isMigrationNeeded()) {
                val legacyConnections = legacySettings.getLegacyConnections()
                logger.debug("Migrating ${legacyConnections.size} legacy connections from BigDataIdeGlobalConnectionSettings")

                val existingIds = getConnections().map { it.innerId }.toSet()
                var migratedCount = 0

                legacyConnections.forEach { legacyConn ->
                    if (legacyConn.innerId !in existingIds) {
                        logger.debug("[MIGRATING] innerId=${legacyConn.innerId}, name=${legacyConn.name}, groupId=${legacyConn.groupId}")
                        addConnection(unpackData(legacyConn))
                        migratedCount++
                    } else {
                        logger.debug("[SKIPPING-DUPLICATE] innerId=${legacyConn.innerId}, name=${legacyConn.name} already exists")
                    }
                }

                logger.debug("Migration complete. Migrated $migratedCount connections, skipped ${legacyConnections.size - migratedCount} duplicates")
                legacySettings.markMigrationComplete()

                // Mark migration as complete, this will be persisted when getState() is called
                legacyMigrationCompleted = true

                // Show notification to user about migration result
                showMigrationNotification(migratedCount)
                migrationNotificationShown = true
            } else {
                logger.debug("No legacy settings file found")
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
        logger.debug("Scheduling migration notification for $migratedCount connections")
        invokeLater {
            logger.debug("Creating notification")
            try {
                val notificationGroup = NotificationGroupManager.getInstance()
                    .getNotificationGroup("kafka")

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

                if (openProjects.isNotEmpty()) {
                    openProjects.forEach { project ->
                        if (!project.isDisposed) {
                            logger.debug("Showing notification in project: ${project.name}")
                            notification.notify(project)
                        }
                    }
                } else {
                    logger.debug("No open projects, showing notification without project context")
                    notification.notify(null)
                }
                logger.debug("Migration notification displayed successfully")
            } catch (e: Exception) {
                logger.warn("Failed to show migration notification", e)
            }
        }
    }

    private fun showNoLegacyNotification() {
        logger.debug("Scheduling no-legacy notification")
        invokeLater {
            logger.debug("Creating no-legacy notification")
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
                logger.debug("No-legacy notification displayed successfully")
            } catch (e: Exception) {
                logger.warn("Failed to show no-legacy notification", e)
            }
        }
    }
}
