package io.confluent.intellijplugin.core.settings

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import io.confluent.intellijplugin.core.util.invokeLater
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
        private val logger = thisLogger()
        fun getInstance(project: Project): LocalConnectionSettings = project.service()
    }

    override fun unpackData(conn: ConnectionData): ConnectionData {
        return super.unpackData(conn).apply { isPerProject = true }
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
     * Called when there's no existing state file (confluent_kafka_settings.xml doesn't exist in project).
     * This is where we check for and migrate legacy connections.
     */
    override fun noStateLoaded() {
        logger.debug("No state file found, checking for legacy connections...")
        super.noStateLoaded()
        migrateLegacyConnections()
    }

    private fun migrateLegacyConnections() {
        try {
            val legacySettings = LegacyLocalConnectionSettings.getInstance(project)

            if (legacySettings.isMigrationNeeded()) {
                val legacyConnections = legacySettings.getLegacyConnections()
                logger.debug("Migrating ${legacyConnections.size} legacy connections from BigDataIdeConnectionSettings")

                val existingIds = getConnections().map { it.innerId }.toSet()
                var migratedCount = 0

                legacyConnections.forEach { legacyConn ->
                    if (legacyConn.innerId !in existingIds) {
                        logger.debug("[MIGRATING] innerId=${legacyConn.innerId}, name=${legacyConn.name}, groupId=${legacyConn.groupId}")
                        addConnection(unpackData(legacyConn))
                        migratedCount++
                    } else {
                        logger.debug("[SKIPPING-DUPLICATE] innerId=${legacyConn.innerId}, name=${legacyConn.name} - already exists")
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
                // For local/per-project settings, no notification needed since they travel with the project
                // and the export/import workaround doesn't apply to them
            }
        } catch (e: Exception) {
            logger.warn("Failed to migrate legacy local connections", e)
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
                    KafkaMessagesBundle.message("migration.notification.title.project"),
                    KafkaMessagesBundle.message("migration.notification.content", migratedCount),
                    NotificationType.INFORMATION
                )

                if (!project.isDisposed) {
                    logger.debug("Showing notification in project: ${project.name}")
                    notification.notify(project)
                    logger.debug("Migration notification displayed successfully")
                } else {
                    logger.debug("Project is disposed, cannot show notification")
                }
            } catch (e: Exception) {
                logger.warn("Failed to show migration notification", e)
            }
        }
    }
}
