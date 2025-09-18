package com.jetbrains.bigdatatools.kafka.settings

import com.intellij.bigdatatools.kafka.icons.BigdatatoolsKafkaIcons
import com.intellij.openapi.project.Project
import com.jetbrains.bigdatatools.kafka.core.settings.connections.ConnectionConfigurable
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData

class KafkaConnectionConfigurable(
  connectionData: KafkaConnectionData,
  project: Project
) : ConnectionConfigurable<KafkaConnectionData, KafkaSettingsCustomizer>(connectionData, project, BigdatatoolsKafkaIcons.Kafka) {
  override fun getHelpTopic() = "big.data.tools.kafka"
  override fun createSettingsCustomizer() = KafkaSettingsCustomizer(project, connectionData, disposable, coroutineScope)
  override fun createConnectionTesting() = KafkaTestingBase(project, settingsCustomizer)
}