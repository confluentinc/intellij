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
     * Gets a unique anonymous device ID for the machine.
     * It is hashed to avoid PII and used to identify the machine in telemetry.
     * @return Unique device ID string or "unknown" if unavailable
     */
    fun getUniqueDeviceId(): String {
        return try {
            val hostname = java.net.InetAddress.getLocalHost().hostName.substringBefore('.')
            val bytes = MessageDigest.getInstance("SHA-256").digest(hostname.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }.take(16)
        } catch (e: Exception) {
            "unknown"
        }
    }
}
