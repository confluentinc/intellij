package io.confluent.intellijplugin.settings

import io.confluent.intellijplugin.icons.BigdatatoolsKafkaIcons
import com.intellij.openapi.project.Project
import io.confluent.intellijplugin.core.settings.connections.ConnectionConfigurable
import io.confluent.intellijplugin.rfs.KafkaConnectionData

class KafkaConnectionConfigurable(
    connectionData: KafkaConnectionData,
    project: Project
) : ConnectionConfigurable<KafkaConnectionData, KafkaSettingsCustomizer>(
    connectionData,
    project,
    BigdatatoolsKafkaIcons.Kafka
) {
    override fun getHelpTopic() = "big.data.tools.kafka"
    override fun createSettingsCustomizer() =
        KafkaSettingsCustomizer(project, connectionData, disposable, coroutineScope)

    override fun createConnectionTesting() = KafkaTestingBase(project, settingsCustomizer)
}