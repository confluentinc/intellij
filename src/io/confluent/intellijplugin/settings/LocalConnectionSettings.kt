package io.confluent.intellijplugin.core.settings

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.ui.dsl.builder.panel
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import io.confluent.intellijplugin.core.util.invokeLater
import io.confluent.intellijplugin.settings.KafkaUIUtils
import io.confluent.intellijplugin.util.KafkaMessagesBundle

@Service(Service.Level.PROJECT)
@State(
    name = "ConfluentIntellijKafkaLocalSettings",
    useLoadedStateAsExisting = false, // This is hack, needed because we need to transfer sensitive data from settings to PasswordSafe
    storages = [
        Storage("confluent_kafka_settings.xml")
    ]
)
class LocalConnectionSettings(private val project: Project) : ConnectionSettingsBase() {
    companion object {
        private val logger = Logger.getInstance(LocalConnectionSettings::class.java)
        fun getInstance(project: Project): LocalConnectionSettings = project.service()
    }

    override fun unpackData(conn: ConnectionData): ConnectionData {
        return super.unpackData(conn).apply { isPerProject = true }
    }

    override fun loadState(state: ConnectionPersistentState) {
        logger.warn("LocalConnectionSettings: Loading state with ${state.connections.size} connections, legacyMigrationCompleted=${state.legacyMigrationCompleted}")
        super.loadState(state)

        // Only attempt migration if not already completed
        if (!legacyMigrationCompleted) {
            migrateLegacyConnections()
        } else {
            logger.warn("LocalConnectionSettings: Legacy migration already completed, skipping")
        }
    }

    /**
     * Called when there's no existing state file (confluent_kafka_settings.xml doesn't exist in project).
     * This is where we check for and migrate legacy connections.
     */
    override fun noStateLoaded() {
        logger.warn("LocalConnectionSettings: No state file found, checking for legacy connections...")
        super.noStateLoaded()
        migrateLegacyConnections()
    }

    private fun migrateLegacyConnections() {
        try {
            val legacySettings = LegacyLocalConnectionSettings.getInstance(project)

            if (legacySettings.isMigrationNeeded()) {
                val legacyConnections = legacySettings.getLegacyConnections()
                logger.warn("LocalConnectionSettings: Migrating ${legacyConnections.size} legacy connections from BigDataIdeConnectionSettings")

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

                logger.warn("LocalConnectionSettings: Migration complete. Migrated $migratedCount connections, skipped ${legacyConnections.size - migratedCount} duplicates")
                legacySettings.markMigrationComplete()

                // Mark migration as complete, this will be persisted when getState() is called
                legacyMigrationCompleted = true

                // Show notification to user about migration result
                showMigrationNotification(migratedCount)
                migrationNotificationShown = true
            } else {
                logger.warn("LocalConnectionSettings: No legacy settings file found")
                // Don't mark migration complete - user might import old settings later
                // But show notification once with workaround info
                if (!migrationNotificationShown) {
                    showNoLegacyNotification()
                    migrationNotificationShown = true
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to migrate legacy local connections", e)
        }
    }

    private fun showMigrationNotification(migratedCount: Int) {
        logger.warn("LocalConnectionSettings: Scheduling migration notification for $migratedCount connections")
        invokeLater {
            logger.warn("LocalConnectionSettings: Inside invokeLater, creating notification")
            try {
                val notificationGroup = NotificationGroupManager.getInstance()
                    .getNotificationGroup("kafka")
                logger.warn("LocalConnectionSettings: Got notification group: $notificationGroup")

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

                if (!project.isDisposed) {
                    logger.warn("LocalConnectionSettings: Showing notification in project: ${project.name}")
                    notification.notify(project)
                    logger.warn("LocalConnectionSettings: Migration notification displayed successfully")
                } else {
                    logger.warn("LocalConnectionSettings: Project is disposed, cannot show notification")
                }
            } catch (e: Exception) {
                logger.error("LocalConnectionSettings: Failed to show migration notification", e)
            }
        }
    }

    private fun showNoLegacyNotification() {
        logger.warn("LocalConnectionSettings: Scheduling no-legacy notification")
        invokeLater {
            logger.warn("LocalConnectionSettings: Inside invokeLater, creating no-legacy notification")
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

                if (!project.isDisposed) {
                    notification.notify(project)
                    logger.warn("LocalConnectionSettings: No-legacy notification displayed successfully")
                } else {
                    logger.warn("LocalConnectionSettings: Project is disposed, cannot show notification")
                }
            } catch (e: Exception) {
                logger.error("LocalConnectionSettings: Failed to show no-legacy notification", e)
            }
        }
    }
}
