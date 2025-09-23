package io.confluent.kafka.settings

import io.confluent.kafka.core.constants.BdtPluginType
import io.confluent.kafka.core.settings.connections.ConnectionGroup
import io.confluent.kafka.core.settings.connections.ConnectionSettingProvider

class KafkaSettingsProvider : ConnectionSettingProvider {
  override val pluginType: BdtPluginType = BdtPluginType.KAFKA

  override fun createConnectionGroups(): List<ConnectionGroup> = listOf(KafkaConnectionGroup())
}