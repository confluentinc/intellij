package io.confluent.intellijplugin.settings.app
import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Persistent storage for Kafka plugin settings.
 * Stored at location: kafka_plugin_settings.xml
 * @see KafkaPluginSettingsConfigurable for UI implementation
 */
@Service
@State(
    name = "ConfluentKafkaPluginSettings",
    storages = [Storage("kafka_plugin_settings.xml")]
)
class KafkaPluginSettings : PersistentStateComponent<KafkaPluginSettings> {
    var enableUsageData: Boolean = true

    // Persistent anonymous machine ID for telemetry. Auto-generated on first access.
    var machineId: String? = null

    override fun getState(): KafkaPluginSettings = this

    override fun loadState(state: KafkaPluginSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): KafkaPluginSettings = service()
    }
}
