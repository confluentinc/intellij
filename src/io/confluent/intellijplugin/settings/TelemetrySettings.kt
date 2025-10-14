package io.confluent.intellijplugin.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.xmlb.XmlSerializerUtil

@Service
@State(
    name = "ConfluentIntellijKafkaTelemetrySettings",
    storages = [Storage("confluent_kafka_telemetry_settings.xml")]
)
class TelemetrySettings : PersistentStateComponent<TelemetrySettings> {

    companion object {
        private val logger = Logger.getInstance(TelemetrySettings::class.java)

        fun getInstance(): TelemetrySettings = service()
    }

    /**
     * Indicates whether the user has consented to sending telemetry data to Segment.
     * Defaults to false (no consent) for privacy-first approach.
     */
    var telemetryConsent: Boolean = false
        set(value) {
            val oldValue = field
            field = value
            if (oldValue != value) {
                logger.debug("Telemetry consent setting accessed - old_value: $oldValue, new_value: $value")
                logger.info("Telemetry consent changed from $oldValue to $value")
            }
        }

    override fun getState() = this

    override fun loadState(state: TelemetrySettings) = try {
        XmlSerializerUtil.copyBean(state, this)
    } catch (ignore: Exception) {
        logger.warn("Failed to load telemetry settings, using defaults", ignore)
    }
}

