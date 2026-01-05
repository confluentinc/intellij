package io.confluent.intellijplugin.core.settings

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import io.confluent.intellijplugin.core.settings.connections.ConnectionData

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

                // Mark migration as complete - this will be persisted when getState() is called
                legacyMigrationCompleted = true
            } else {
                logger.warn("LocalConnectionSettings: No legacy connections found to migrate")
                // Even if no legacy connections exist, mark as complete so we don't check again
                legacyMigrationCompleted = true
            }
        } catch (e: Exception) {
            logger.warn("Failed to migrate legacy local connections", e)
        }
    }
}
