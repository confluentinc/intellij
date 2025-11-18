package io.confluent.intellijplugin.telemetry

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.diagnostic.thisLogger
import io.confluent.intellijplugin.core.constants.BdtPlugins
import java.util.UUID

/**
 * Shared utilities for telemetry operations.
 */
object TelemetryUtils {
    private val logger = thisLogger()
    private const val MACHINE_ID_KEY = "io.confluent.intellijplugin.commonMachineId"

    private val cachedMachineId: String by lazy { loadOrCreateMachineId() }

    /**
     * Gets a persistent anonymous machine ID for telemetry.
     * Lazily generated on first access and cached in memory.
     */
    fun commonMachineId(): String = cachedMachineId

    private fun loadOrCreateMachineId(): String = try {
        val properties = PropertiesComponent.getInstance()
        var machineId = properties.getValue(MACHINE_ID_KEY)

        logger.info("Loading machine ID from key: $MACHINE_ID_KEY")
        logger.info("Found existing machine ID: ${machineId ?: "null"}")

        if (machineId == null || !isValidUuid(machineId)) {
            machineId = UUID.randomUUID().toString()
            properties.setValue(MACHINE_ID_KEY, machineId)
            logger.info("Generated and saved new machine ID: $machineId")
        } else {
            logger.info("Using existing machine ID: $machineId")
        }

        machineId
    } catch (e: Exception) {
        logger.warn("Failed to get or create common machine ID", e)
        "unknown"
    }

    private fun isValidUuid(value: String): Boolean {
        return try {
            UUID.fromString(value)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets the current Kafka plugin version.
     * @return Plugin version string or "unknown" if unavailable
     */
    fun getPluginVersion(): String {
        return try {
            val pluginId = PluginId.getId(BdtPlugins.KAFKA_ID)
            PluginManagerCore.getPlugin(pluginId)?.version ?: "unknown"
        } catch (e: Exception) {
            logger.warn("Failed to get plugin version", e)
            "unknown"
        }
    }
}
