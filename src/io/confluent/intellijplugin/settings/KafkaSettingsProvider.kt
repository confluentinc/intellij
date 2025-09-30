package io.confluent.intellijplugin.settings

import io.confluent.intellijplugin.core.constants.BdtPluginType
import io.confluent.intellijplugin.core.settings.connections.ConnectionGroup
import io.confluent.intellijplugin.core.settings.connections.ConnectionSettingProvider

class KafkaSettingsProvider : ConnectionSettingProvider {
  override val pluginType: BdtPluginType = BdtPluginType.KAFKA

  override fun createConnectionGroups(): List<ConnectionGroup> = listOf(KafkaConnectionGroup())
}