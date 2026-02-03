package io.confluent.intellijplugin.core.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.thisLogger

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
        private val logger = thisLogger()
        fun getInstance(): LegacyGlobalConnectionSettings = service()
    }

    private var legacyState: ConnectionPersistentState? = null

    override fun getState(): ConnectionPersistentState? = legacyState

    override fun loadState(state: ConnectionPersistentState) {
        logger.debug("Found ${state.connections.size} global legacy connections")
        state.connections.forEach { conn ->
            logger.debug("innerId=${conn.innerId}, name=${conn.name}, groupId=${conn.groupId}, fqn=${conn.fqn}")
        }
        legacyState = state
    }

    fun getLegacyConnections(): List<ExtendedConnectionData> = legacyState?.connections ?: emptyList()
}
