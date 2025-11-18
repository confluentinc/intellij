package io.confluent.intellijplugin.telemetry

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.SystemInfo
import io.confluent.intellijplugin.core.constants.BdtPlugins
import java.util.UUID
import java.security.MessageDigest

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

        if (machineId == null || !isValidUuid(machineId)) {
            machineId = UUID.randomUUID().toString()
            properties.setValue(MACHINE_ID_KEY, machineId)
            logger.debug("Generated and saved new machine ID: $machineId")
        } else {
            logger.debug("Using existing machine ID: $machineId")
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

    /**
     * Gets an anonymised hostname hash for the machine.
     * The hostname is hashed using SHA-256
     * @return 16-character hexadecimal hash string or "unknown" if unavailable
     */
    fun getAnonymisedHostname(): String {
        return try {
            val hostname = java.net.InetAddress.getLocalHost().hostName.substringBefore('.')
            val bytes = MessageDigest.getInstance("SHA-256").digest(hostname.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }.take(16)
        } catch (e: Exception) {
            "unknown"
        }
    }

    /**
     * Gets the platform name.
     * @return Platform name string
     */
    fun getPlatformName(): String {
        return when {
            SystemInfo.isMac -> "darwin"
            SystemInfo.isWindows -> "win32"
            SystemInfo.isLinux -> "linux"
            else -> SystemInfo.OS_NAME.lowercase()
        }
    }
}
