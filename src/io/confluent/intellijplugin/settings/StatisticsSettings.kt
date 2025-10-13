package io.confluent.intellijplugin.core.settings

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

@Service
// Currently we only need to persist ids of broken connection settings, to prevent duplicates sent to statistics.
@State(
    name = "ConfluentIntellijKafkaStatisticsSettings",
    storages = [Storage("confluent_kafka_statistics_settings.xml")]
)
class StatisticsSettings : PersistentStateComponent<StatisticsSettings> {

    var reportedBrokenConnections = HashSet<String>()

    override fun getState() = this

    override fun loadState(state: StatisticsSettings) = try {
        XmlSerializerUtil.copyBean(state, this)
    } catch (ignore: Exception) {
    }

    companion object {
        fun getInstance(): StatisticsSettings = service()
    }
}