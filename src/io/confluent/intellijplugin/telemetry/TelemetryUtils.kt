package io.confluent.intellijplugin.telemetry

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.SystemInfo
import io.confluent.intellijplugin.core.constants.BdtPlugins
import java.security.MessageDigest

/**
 * Shared utilities for telemetry operations.
 */
object TelemetryUtils {
    private val logger = thisLogger()

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
     * The hostname is hashed using SHA-256 and used as a server identifier
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
