package io.confluent.intellijplugin.telemetry

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.PermanentInstallationID
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import io.sentry.Sentry
import io.sentry.SentryEvent

object SentryClient {
    private val logger = Logger.getInstance(SentryClient::class.java)

    init {
        // Skip Sentry initialization in test environment to avoid conflicts with IntelliJ test framework
        if (isTestEnvironment()) {
            logger.info("Skipping Sentry initialization in test environment")
        } else {
            try {
                logger.info("Initializing Sentry")
                Sentry.init { options ->
                    options.dsn = SentryConfig.DSN
                    options.isDebug = false
                    options.release = TelemetryUtils.getPluginVersion()
                    options.serverName = TelemetryUtils.getAnonymisedHostname()

                    options.setBeforeSend { event, _ ->
                        // Filter out non-plugin errors before sending to Sentry
                        if (isPluginRelatedError(event)) {
                            addDefaultTags(event)
                            event
                        } else {
                            logger.debug("Dropping non-plugin error from Sentry: ${event.throwable?.javaClass?.name}")
                            null  // Return null to prevent sending this event
                        }
                    }
                }
                logger.info("Sentry initialized successfully")
            } catch (e: Exception) {
                logger.error("Sentry initialization failed", e)
            }
        }
    }

    private fun isTestEnvironment(): Boolean {
        return System.getProperty("idea.test.execution.policy") != null
    }

    private fun addDefaultTags(event: SentryEvent) {
        val appInfo = ApplicationInfo.getInstance()

        event.setTag("productName", appInfo.fullApplicationName)
        event.setTag("productVersion", appInfo.fullVersion)
        event.setTag("pluginVersion", TelemetryUtils.getPluginVersion())
        event.setTag("ide.build", appInfo.build.asString())
        event.setTag("platform", TelemetryUtils.getPlatformName())
        event.setTag("arch", SystemInfo.OS_ARCH)
        event.setTag("os", "${SystemInfo.OS_NAME} ${SystemInfo.OS_VERSION}")
    }

    fun captureException(exception: Throwable) {
        try {
            Sentry.captureException(exception)
            logger.debug("Exception captured and sent to Sentry")
        } catch (e: Exception) {
            logger.error("Failed to capture exception to Sentry", e)
        }
    }

    /**
     * Checks if a Sentry event originated from plugin code.
     * Walks the entire cause chain and suppressed exceptions to detect plugin frames.
     *
     * @param event The Sentry event to check
     * @return true if the error is plugin-related, false otherwise
     */
    internal fun isPluginRelatedError(event: SentryEvent): Boolean {
        val throwable = event.throwable ?: return false
        return containsPluginFrame(throwable)
    }

    private fun containsPluginFrame(throwable: Throwable): Boolean {
        val visited = mutableSetOf<Throwable>()
        val toVisit = ArrayDeque<Throwable>()
        toVisit.add(throwable)

        while (toVisit.isNotEmpty()) {
            val current = toVisit.removeFirst()

            // Avoid infinite loops from circular references
            if (!visited.add(current)) {
                continue
            }

            // Check if any stack frame belongs to plugin package
            if (current.stackTrace.any { frame ->
                frame.className == TelemetryUtils.PLUGIN_PACKAGE ||
                frame.className.startsWith("${TelemetryUtils.PLUGIN_PACKAGE}.")
            }) {
                return true
            }

            // Walk cause chain and suppressed exceptions
            current.cause?.let(toVisit::addLast)
            current.suppressed.forEach(toVisit::addLast)
        }

        return false
    }
}
