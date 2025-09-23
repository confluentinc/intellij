package io.confluent.kafka.core.settings.connections

import io.confluent.kafka.core.constants.BdtPluginType

class GeneralConnectionSettingsProvider : ConnectionSettingProvider {
  override val pluginType: BdtPluginType = BdtPluginType.KAFKA

  override fun createConnectionGroups(): List<ConnectionGroup> = listOf(BrokerConnectionGroup())
}