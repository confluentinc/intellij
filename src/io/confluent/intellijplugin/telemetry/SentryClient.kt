package io.confluent.intellijplugin.telemetry

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.PermanentInstallationID
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import io.sentry.Sentry

object SentryClient {
    private val logger = Logger.getInstance(SentryClient::class.java)

    init {
        try {
            logger.info("Initializing Sentry")
            Sentry.init { options ->
                options.dsn = SentryConfig.DSN
                options.isDebug = false
                options.release = TelemetryUtils.getPluginVersion()
                options.serverName = getDeviceId()
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
        event.setTag("pluginVersion", TelemetryUtils.getPluginVersion())
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

    // Use anonymous device ID to avoid PII 
    private fun getDeviceId(): String {
        return try {
            PermanentInstallationID.get()
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
