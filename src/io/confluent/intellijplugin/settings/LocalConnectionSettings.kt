package io.confluent.intellijplugin.core.settings

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializer
import io.confluent.intellijplugin.core.constants.BdtPlugins
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
        logger.info("Loading state with ${state.connections.size} connections, legacyMigrationCompleted=${state.legacyMigrationCompleted}")
        super.loadState(state)

        // Only attempt migration if not already completed
        if (!legacyMigrationCompleted) {
            migrateLegacyConnections()
        } else {
            logger.info("Legacy migration already completed, skipping")
        }
    }

    /**
     * Called when there's no existing state file (confluent_kafka_settings.xml doesn't exist in project).
     * This is where we check for and migrate legacy connections.
     */
    override fun noStateLoaded() {
        logger.info("No state file found, checking for legacy connections...")
        super.noStateLoaded()
        migrateLegacyConnections()
    }

    private fun migrateLegacyConnections() {
        try {
            // Get legacy connections - handling the case where BDT plugin might be installed
            // If BDT is installed, its LocalConnectionSettings is already registered with the same
            // component name "BigDataIdeConnectionSettings", so we access it directly to avoid conflicts
            val legacyResult = getLegacyConnectionsAvoidingConflict()

            if (legacyResult != null && legacyResult.connections.isNotEmpty()) {
                val legacyConnections = legacyResult.connections
                logger.info("Migrating ${legacyConnections.size} legacy connections from BigDataIdeConnectionSettings")

                val existingIds = getConnections().map { it.innerId }.toSet()
                var migratedCount = 0

                legacyConnections.forEach { legacyConn ->
                    if (legacyConn.innerId !in existingIds) {
                        logger.info("[MIGRATING] innerId=${legacyConn.innerId}, name=${legacyConn.name}, groupId=${legacyConn.groupId}")
                        addConnection(unpackData(legacyConn))
                        migratedCount++
                    } else {
                        logger.info("[SKIPPING-DUPLICATE] innerId=${legacyConn.innerId}, name=${legacyConn.name} - already exists")
                    }
                }

                logger.info("Migration complete. Migrated $migratedCount connections, skipped ${legacyConnections.size - migratedCount} duplicates")
                legacyResult.markComplete?.invoke()

                // Mark migration as complete, this will be persisted when getState() is called
                legacyMigrationCompleted = true

                // Show notification to user about migration result
                showMigrationNotification(migratedCount)
                migrationNotificationShown = true
            } else {
                logger.info("No legacy settings file found")
                // For local/per-project settings, no notification needed since they travel with the project
                // and the export/import workaround doesn't apply to them
            }
        } catch (e: Exception) {
            logger.warn("Failed to migrate legacy local connections", e)
        }
    }

    private data class LegacyConnectionsResult(
        val connections: List<ExtendedConnectionData>,
        val markComplete: (() -> Unit)?
    )

    /**
     * Gets legacy connections, handling the case where Big Data Tools plugin might be installed.
     *
     * When BDT is installed, its LocalConnectionSettings is already registered with the component
     * name "BigDataIdeConnectionSettings". If we try to register our LegacyLocalConnectionSettings
     * with the same name, IntelliJ throws a conflict error. To avoid this, we access BDT's service
     * directly via reflection when BDT is installed.
     */
    private fun getLegacyConnectionsAvoidingConflict(): LegacyConnectionsResult? {
        return if (BdtPlugins.isFullPluginInstalled()) {
            logger.info("Big Data Tools plugin is installed, accessing its LocalConnectionSettings directly")
            getConnectionsFromBdtService()
        } else {
            // BDT not installed, safe to use our LegacyLocalConnectionSettings
            getConnectionsFromOurLegacyService()
        }
    }

    /**
     * Uses reflection to access Big Data Tools plugin's LocalConnectionSettings service.
     * This avoids the component name conflict by reading from BDT's already-registered component.
     *
     * Since our plugin took over the BDT Kafka plugin with identical data structures (just different
     * namespace), we serialize BDT's state to XML and deserialize as our type. The existing RENAME_MAP
     * in ConnectionSettingsBase handles class name translation during deserialization.
     */
    private fun getConnectionsFromBdtService(): LegacyConnectionsResult? {
        return try {
            // Get BDT plugin's classloader to load its classes
            val bdtPluginId = PluginId.findId(BdtPlugins.FULL_ID)
            val bdtPlugin = bdtPluginId?.let { PluginManagerCore.getPlugin(it) }
            val bdtClassLoader = bdtPlugin?.pluginClassLoader

            if (bdtClassLoader == null) {
                logger.warn("Could not get Big Data Tools plugin classloader")
                return null
            }

            val bdtClass = bdtClassLoader.loadClass("com.jetbrains.bigdatatools.common.settings.LocalConnectionSettings")
            val bdtService = project.getService(bdtClass)

            if (bdtService != null) {
                // Call getState() to get the persisted state
                val getStateMethod = bdtClass.getMethod("getState")
                val bdtState = getStateMethod.invoke(bdtService)

                if (bdtState == null) {
                    logger.info("BDT LocalConnectionSettings.getState() returned null")
                    return null
                }

                // Serialize BDT's state to XML and deserialize as our ConnectionPersistentState
                // This leverages the existing RENAME_MAP to handle class name translation
                val xmlElement = XmlSerializer.serialize(bdtState)
                logger.info("Serialized BDT local state to XML: ${xmlElement}")

                val ourState = XmlSerializer.deserialize(xmlElement, ConnectionPersistentState::class.java)
                logger.info("Deserialized ${ourState.connections.size} local connections from BDT")

                if (ourState.connections.isEmpty()) {
                    logger.info("No connections found in BDT LocalConnectionSettings")
                    return null
                }

                LegacyConnectionsResult(ourState.connections, null) // No markComplete needed for BDT
            } else {
                logger.warn("Could not get Big Data Tools LocalConnectionSettings service instance")
                null
            }
        } catch (e: Exception) {
            logger.warn("Could not access Big Data Tools LocalConnectionSettings", e)
            null
        }
    }

    /**
     * Gets connections from our LegacyLocalConnectionSettings.
     * Only called when BDT is NOT installed, so no conflict will occur.
     */
    private fun getConnectionsFromOurLegacyService(): LegacyConnectionsResult? {
        val legacySettings = LegacyLocalConnectionSettings.getInstance(project)
        return if (legacySettings.isMigrationNeeded()) {
            LegacyConnectionsResult(
                connections = legacySettings.getLegacyConnections(),
                markComplete = { legacySettings.markMigrationComplete() }
            )
        } else {
            null
        }
    }

    private fun showMigrationNotification(migratedCount: Int) {
        logger.info("Scheduling migration notification for $migratedCount connections")
        invokeLater {
            logger.info("Creating notification")
            try {
                val notificationGroup = NotificationGroupManager.getInstance()
                    .getNotificationGroup("kafka")

                val notification = notificationGroup.createNotification(
                    KafkaMessagesBundle.message("migration.notification.title.project"),
                    KafkaMessagesBundle.message("migration.notification.content", migratedCount),
                    NotificationType.INFORMATION
                )

                if (!project.isDisposed) {
                    logger.info("Showing notification in project: ${project.name}")
                    notification.notify(project)
                    logger.info("Migration notification displayed successfully")
                } else {
                    logger.info("Project is disposed, cannot show notification")
                }
            } catch (e: Exception) {
                logger.warn("Failed to show migration notification", e)
            }
        }
    }
}
