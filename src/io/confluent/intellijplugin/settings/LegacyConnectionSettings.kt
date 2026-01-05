package io.confluent.intellijplugin.core.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

/**
 * Legacy global connection settings reader.
 * Reads from the old BigDataTools plugin state name stored in "bigdataide_settings.xml".
 *
 * This is used for migration purposes only, connections are read once and merged
 * into the new ConfluentIntellijKafkaGlobalSettings.
 */
@State(
    name = "BigDataIdeGlobalConnectionSettings",
    storages = [Storage("bigdataide_settings.xml")]
)
@Service
class LegacyGlobalConnectionSettings : PersistentStateComponent<ConnectionPersistentState> {
    companion object {
        private val logger = Logger.getInstance(LegacyGlobalConnectionSettings::class.java)
        fun getInstance(): LegacyGlobalConnectionSettings = service()
    }

    private var legacyState: ConnectionPersistentState? = null
    private var migrationComplete = false

    override fun getState(): ConnectionPersistentState? = null // Never save, read-only

    override fun loadState(state: ConnectionPersistentState) {
        logger.warn("LegacyGlobalConnectionSettings: Found ${state.connections.size} legacy connections")
        state.connections.forEach { conn ->
            logger.warn("[LEGACY-GLOBAL] innerId=${conn.innerId}, name=${conn.name}, groupId=${conn.groupId}, fqn=${conn.fqn}")
        }
        legacyState = state
    }

    fun getLegacyConnections(): List<ExtendedConnectionData> {
        return legacyState?.connections ?: emptyList()
    }

    fun markMigrationComplete() {
        migrationComplete = true
        legacyState = null // Clear to free memory
    }

    fun isMigrationNeeded(): Boolean = !migrationComplete && legacyState != null
}

/**
 * Legacy local (project-level) connection settings reader.
 * Reads from the old BigDataTools plugin state name: "BigDataIdeConnectionSettings"
 * stored in "bigdataide_settings.xml".
 *
 * This is used for migration purposes only - connections are read once and merged
 * into the new ConfluentIntellijKafkaLocalSettings.
 */
@State(
    name = "BigDataIdeConnectionSettings",
    storages = [Storage("bigdataide_settings.xml")]
)
@Service(Service.Level.PROJECT)
class LegacyLocalConnectionSettings : PersistentStateComponent<ConnectionPersistentState> {
    companion object {
        private val logger = Logger.getInstance(LegacyLocalConnectionSettings::class.java)
        fun getInstance(project: Project): LegacyLocalConnectionSettings = project.service()
    }

    private var legacyState: ConnectionPersistentState? = null
    private var migrationComplete = false

    override fun getState(): ConnectionPersistentState? = null // Never save, read-only

    override fun loadState(state: ConnectionPersistentState) {
        logger.warn("LegacyLocalConnectionSettings: Found ${state.connections.size} legacy connections")
        state.connections.forEach { conn ->
            logger.warn("[LEGACY-LOCAL] innerId=${conn.innerId}, name=${conn.name}, groupId=${conn.groupId}, fqn=${conn.fqn}")
        }
        legacyState = state
    }

    fun getLegacyConnections(): List<ExtendedConnectionData> {
        return legacyState?.connections ?: emptyList()
    }

    fun markMigrationComplete() {
        migrationComplete = true
        legacyState = null // Clear to free memory
    }

    fun isMigrationNeeded(): Boolean = !migrationComplete && legacyState != null
}
