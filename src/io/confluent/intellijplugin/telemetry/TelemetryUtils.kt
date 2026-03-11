package io.confluent.intellijplugin.telemetry

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.annotations.VisibleForTesting
import io.confluent.intellijplugin.core.constants.BdtPlugins
import io.confluent.intellijplugin.settings.app.KafkaPluginSettings
import java.net.InetAddress
import java.security.MessageDigest
import java.util.UUID

/**
 * Shared utilities for telemetry operations.
 */
object TelemetryUtils {
    private val logger = thisLogger()
    internal const val MACHINE_ID_KEY = "io.confluent.intellijplugin.commonMachineId"

    @VisibleForTesting
    internal var cachedMachineId: Lazy<String> = lazy { loadOrCreateMachineId() }

    /**
     * Gets a persistent anonymous machine ID for telemetry.
     * Stored in plugin settings kafka_plugin_settings.xml and auto-generated on first access.
     */
    fun commonMachineId(): String = cachedMachineId.value

    @VisibleForTesting
    internal fun resetCachedMachineId() {
        cachedMachineId = lazy { loadOrCreateMachineId() }
    }

    @VisibleForTesting
    internal fun loadOrCreateMachineId(): String = try {
        val settings = KafkaPluginSettings.getInstance()
        var machineId = settings.machineId

        // Migration to check legacy PropertiesComponent if not in settings
        if (machineId.isNullOrBlank()) {
            val properties = PropertiesComponent.getInstance()
            machineId = properties.getValue(MACHINE_ID_KEY)
        }

        // Generate new if still not found or invalid
        if (machineId.isNullOrBlank() || !isValidUuid(machineId)) {
            machineId = UUID.randomUUID().toString()
            logger.debug("Generated new machine ID: $machineId")
        } else {
            logger.debug("Using existing machine ID: $machineId")
        }

        // Always save to persistent settings
        settings.machineId = machineId
        machineId
    } catch (e: Exception) {
        logger.warn("Failed to get or create common machine ID", e)
        "unknown"
    }

    @VisibleForTesting
    internal fun isValidUuid(value: String): Boolean {
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
            val hostname = InetAddress.getLocalHost().hostName.substringBefore('.')
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
