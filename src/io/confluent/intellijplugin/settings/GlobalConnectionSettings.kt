package io.confluent.intellijplugin.core.settings

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger

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

                // Mark migration as complete - this will be persisted when getState() is called
                legacyMigrationCompleted = true
            } else {
                logger.warn("GlobalConnectionSettings: No legacy connections found to migrate")
                // Even if no legacy connections exist, mark as complete so we don't check again
                legacyMigrationCompleted = true
            }
        } catch (e: Exception) {
            logger.warn("Failed to migrate legacy global connections", e)
        }
    }
}
