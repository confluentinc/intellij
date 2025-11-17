package io.confluent.intellijplugin.telemetry

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.SystemInfo
import io.confluent.intellijplugin.core.constants.BdtPlugins
import io.sentry.Sentry

object SentryClient {
    private val logger = Logger.getInstance(SentryClient::class.java)
    
    init {
        try {
            logger.info("Initializing Sentry")
            Sentry.init { options ->
                options.dsn = SentryConfig.DSN
                options.isDebug = false
                options.release = getPluginVersion()
                options.serverName = getHostname()
                options.setBeforeSend { event, _ ->
                    addDefaultTags(event)
                    event
                }
            }
            logger.info("Sentry initialized successfully")
        } catch (e: Exception) {
            logger.error("Sentry initialization failed", e)
        }
    }

    private fun addDefaultTags(event: io.sentry.SentryEvent) {
        val appInfo = ApplicationInfo.getInstance()
        
        event.setTag("productName", appInfo.fullApplicationName)
        event.setTag("productVersion", appInfo.fullVersion)
        event.setTag("pluginVersion", getPluginVersion())
        event.setTag("pluginActivated", "true")
        event.setTag("ide.build", appInfo.build.asString())
        event.setTag("platform", getPlatformName())
        event.setTag("arch", SystemInfo.OS_ARCH)
        event.setTag("os", "${SystemInfo.OS_NAME} ${SystemInfo.OS_VERSION}")
    }
    
    private fun getPlatformName(): String {
        return when {
            SystemInfo.isMac -> "darwin"
            SystemInfo.isWindows -> "win32"
            SystemInfo.isLinux -> "linux"
            else -> SystemInfo.OS_NAME.lowercase()
        }
    }
    
    // Sentry auto sets the "server_name" tag to the IP address 
    // We want to override it to the machine's hostname
    private fun getHostname(): String {
        return try {
            if (SystemInfo.isMac) {
                // Get ComputerName (asset tag) instead of LocalHostName (friendly name)
                Runtime.getRuntime().exec("scutil --get ComputerName")
                    .inputStream.bufferedReader().readText().trim()
                    .ifBlank { java.net.InetAddress.getLocalHost().hostName.substringBefore('.') }
            } else {
                java.net.InetAddress.getLocalHost().hostName.substringBefore('.')
            }
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    private fun getPluginVersion(): String {
        return try {
            val pluginId = PluginId.getId(BdtPlugins.KAFKA_ID)
            PluginManagerCore.getPlugin(pluginId)?.version ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    fun captureException(exception: Throwable) {
        try {
            Sentry.captureException(exception)
            logger.debug("Exception captured and sent to Sentry")
        } catch (e: Exception) {
            logger.error("Failed to capture exception to Sentry", e)
        }
    }
}
