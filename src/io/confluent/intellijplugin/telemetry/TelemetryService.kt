package io.confluent.intellijplugin.telemetry

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.segment.analytics.Analytics
import io.confluent.intellijplugin.settings.app.KafkaPluginSettings
import com.intellij.openapi.application.PermanentInstallationID
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.SystemInfo
import com.segment.analytics.messages.IdentifyMessage
import com.segment.analytics.messages.TrackMessage
import io.confluent.intellijplugin.core.constants.BdtPlugins

/**
 * Application-level service to handle Segment analytics instantiation and tracking telemetry events.
 *
 * Lazy initialized when the first telemetry event is triggered.
 *
 * @see Telemetry for usage.
 */
@Service(Service.Level.APP)
class TelemetryService : Disposable {
    private val logger = thisLogger()

    internal var analytics: Analytics? = null
    private var warnedAboutSegmentKey = false

    init {
        initialize()
    }

    companion object {
        fun getInstance(): TelemetryService = service()
    }

    private fun initialize() {
        if (analytics == null) {
            val writeKey = System.getenv("SEGMENT_WRITE_KEY")
            if (writeKey.isNullOrBlank()) {
                // If we don't have a key, assume we're in dev mode and skip initialization
                if (!warnedAboutSegmentKey) {
                    warnedAboutSegmentKey = true
                    logger.debug("No Segment write key found, telemetry disabled")
                }
                return
            }

            try {
                analytics = Analytics.builder(writeKey).build()
                logger.debug("Telemetry service initialized successfully")
            } catch (e: Exception) {
                logger.error("Failed to initialize telemetry service", e)
            }
        }
    }


    /**
     * Gets the anonymous user ID for telemetry.
     * Uses IntelliJ's permanent installation ID.
     */
    private fun getUserId(): String {
        return try {
            PermanentInstallationID.get()
        } catch (e: Exception) {
            logger.warn("Failed to get installation ID", e)
            "unknown"
        }
    }

    /**
     * Gets the current Kafka plugin version.
     */
    private fun getPluginVersion(): String {
        return try {
            val pluginId = PluginId.getId(BdtPlugins.KAFKA_ID)
            PluginManagerCore.getPlugin(pluginId)?.version ?: "unknown"
        } catch (e: Exception) {
            logger.warn("Failed to get plugin version", e)
            "unknown"
        }
    }

    /**
     * Builds the common context information for Segment events.
     */
    private fun buildContext(): Map<String, Any> = buildMap {
        put("os", mapOf(
            "name" to SystemInfo.OS_NAME,
            "version" to SystemInfo.OS_VERSION,
            "arch" to SystemInfo.OS_ARCH
        ))
    }

    /**
     * Builds the common properties for Segment events.
     */
    private fun buildCommonProperties(): Map<String, String> = buildMap {
        // IDE information
        val appInfo = ApplicationInfo.getInstance()
        put("ideName", appInfo.fullApplicationName)
        put("ideVersion", appInfo.fullVersion)
        put("ideBuild", appInfo.build.asString())
        put("ideMajorVersion", appInfo.majorVersion)
        put("ideIsEAP", appInfo.isEAP.toString())
        put("pluginName", "confluent.intellijplugin")
        put("pluginVersion", getPluginVersion())
    }

    /**
     * Track an event if telemetry is enabled and user has opted in.
     * Note: Use Telemetry.logUsage for type-safe event tracking.
     */
    fun sendTrackEvent(event: String, properties: Map<String, Any>) {
        if (analytics == null) {
            logger.debug("Event not tracked - analytics not initialized: $event")
            return
        }
        if (!KafkaPluginSettings.getInstance().enableUsageData) {
            logger.debug("Event not tracked - user has not opted in: $event")
            return
        }

        try {
            analytics?.enqueue(TrackMessage.builder(event)
                    .userId(getUserId())
                    .context(buildContext())
                    .properties(properties + buildCommonProperties())
            )
            logger.debug("Event tracked: $event")
        } catch (e: Exception) {
            logger.warn("Failed to track event: $event", e)
        }
    }

    /**
     * Send an identify event to Segment.
     * Note: Use Telemetry.logUser
     */
    fun sendIdentifyEvent(traits: Map<String, Any>) {
        if (analytics == null) {
            logger.debug("User event not tracked - analytics not initialized")
            return
        }
        if (!KafkaPluginSettings.getInstance().enableUsageData) {
            logger.debug("User event not tracked - user has not opted in")
            return
        }

        try {
            analytics?.enqueue(IdentifyMessage.builder()
                .userId(getUserId())
                .context(buildContext())
                .traits(traits + buildCommonProperties())
            )
        } catch (e: Exception) {
            logger.warn("Failed to send identify", e)
        }
    }

    /**
     * Flushes any pending events to Segment.
     * Useful during plugin shutdown.
     */
    fun flush() {
        try {
            analytics?.flush()
            logger.debug("Flushed telemetry events")
        } catch (e: Exception) {
            logger.warn("Failed to flush telemetry", e)
        }
    }

    /**
     * Shutdown the analytics client gracefully.
     */
    fun shutdown() {
        analytics?.flush()
        analytics?.shutdown()
        logger.debug("Telemetry service shutdown")
    }

    /**
     * Called when the service is disposed (plugin unload, IDE shutdown).
     */
    override fun dispose() {
        shutdown()
    }
}
