package io.confluent.intellijplugin.core.settings

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.xmlb.XmlSerializer
import io.confluent.intellijplugin.core.constants.BdtPlugins
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
     * Called when there's no existing state file (confluent_kafka_settings.xml doesn't exist).
     * This is where we check for and migrate legacy connections.
     */
    override fun noStateLoaded() {
        logger.info("No state file found, checking for legacy connections...")
        super.noStateLoaded()
        migrateLegacyConnections()
    }

    private fun migrateLegacyConnections() {
        try {
            // Get legacy connections, handling the case where BDT plugin might be installed
            // If BDT is installed, its GlobalConnectionSettings is already registered with the same
            // component name "BigDataIdeGlobalConnectionSettings", so we access it directly to avoid conflicts
            val legacyConnections = if (BdtPlugins.isFullPluginInstalled()) {
                logger.info("Big Data Tools plugin is installed, accessing its GlobalConnectionSettings directly")
                getConnectionsFromBdtService()
            } else {
                // Fallback to creating a new service that access the same "BigDataIdeGlobalConnectionSettings" component
                LegacyGlobalConnectionSettings.getInstance().getLegacyConnections()
            }

            if (legacyConnections.isNotEmpty()) {
                logger.info("Migrating ${legacyConnections.size} legacy connections from BigDataIdeGlobalConnectionSettings")

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

                logger.info("Migration complete. Migrated $migratedCount connections, skipped ${legacyConnections.size - migratedCount} duplicates")
                // Mark migration as complete, this will be persisted when getState() is called
                legacyMigrationCompleted = true

                // Show notification to user about migration result
                showMigrationNotification(migratedCount)
                migrationNotificationShown = true
            } else {
                logger.info("No legacy settings file found")
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
    /**
     * Uses reflection to access Big Data Tools plugin's GlobalConnectionSettings service.
     * This avoids the component name conflict by reading from BDT's already-registered component.
     * by reading from BDT's already-registered component.
     *
     * When BDT is installed, its GlobalConnectionSettings is already registered with the component
     * name "BigDataIdeConnectionSettings". If we try to register our LegacyLocalConnectionSettings
     * with the same name, IntelliJ throws a conflict error.
     */
    private fun getConnectionsFromBdtService(): List<ExtendedConnectionData> {
        return try {
            val bdtPluginId = PluginId.findId(BdtPlugins.FULL_ID)
            val bdtPlugin = bdtPluginId?.let { PluginManagerCore.getPlugin(it) }
            val bdtClassLoader = bdtPlugin?.pluginClassLoader

            if (bdtClassLoader == null) {
                logger.warn("Could not get Big Data Tools plugin classloader")
                return emptyList()
            }

            val bdtClass = bdtClassLoader.loadClass("com.jetbrains.bigdatatools.common.settings.GlobalConnectionSettings")
            val bdtService = ApplicationManager.getApplication().getService(bdtClass)

            if (bdtService != null) {
                // Call getState() to get the persisted state
                val getStateMethod = bdtClass.getMethod("getState")
                val bdtState = getStateMethod.invoke(bdtService) ?: return emptyList()

                // Serialize BDT's state to XML and deserialize as our ConnectionPersistentState
                // This leverages the existing RENAME_MAP to handle class name translation
                val xmlElement = XmlSerializer.serialize(bdtState)
                logger.debug("Serialized BDT state to XML: ${xmlElement}")

                val ourState = XmlSerializer.deserialize(xmlElement, ConnectionPersistentState::class.java)
                logger.info("Deserialized ${ourState.connections.size} connections from BDT")
                ourState.connections
            } else {
                logger.warn("Could not get Big Data Tools GlobalConnectionSettings service instance")
                emptyList()
            }
        } catch (e: Exception) {
            logger.warn("Could not access Big Data Tools GlobalConnectionSettings", e)
            emptyList()
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
