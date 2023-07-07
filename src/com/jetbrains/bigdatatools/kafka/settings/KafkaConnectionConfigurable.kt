package com.jetbrains.bigdatatools.kafka.settings

import com.intellij.bigdatatools.kafka.icons.BigdatatoolsKafkaIcons
import com.intellij.openapi.project.Project
import com.jetbrains.bigdatatools.common.settings.connections.ConnectionConfigurable
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData

class KafkaConnectionConfigurable(connectionData: KafkaConnectionData, project: Project) :
  ConnectionConfigurable<KafkaConnectionData>(connectionData, project, BigdatatoolsKafkaIcons.Kafka) {
  private val settingsCustomizer: KafkaSettingsCustomizer
    get() = KafkaSettingsCustomizer(project, connectionData, disposable)

  override fun getHelpTopic() = "big.data.tools.kafka"
  override fun createSettingsCustomizer() = settingsCustomizer
  override fun createConnectionTesting() = KafkaTestingBase(project, settingsCustomizer)
}