package io.confluent.kafka.settings

import io.confluent.kafka.icons.BigdatatoolsKafkaIcons
import com.intellij.openapi.project.Project
import io.confluent.kafka.core.settings.connections.ConnectionConfigurable
import io.confluent.kafka.rfs.KafkaConnectionData

class KafkaConnectionConfigurable(
  connectionData: KafkaConnectionData,
  project: Project
) : ConnectionConfigurable<KafkaConnectionData, KafkaSettingsCustomizer>(connectionData, project, BigdatatoolsKafkaIcons.Kafka) {
  override fun getHelpTopic() = "big.data.tools.kafka"
  override fun createSettingsCustomizer() = KafkaSettingsCustomizer(project, connectionData, disposable, coroutineScope)
  override fun createConnectionTesting() = KafkaTestingBase(project, settingsCustomizer)
}