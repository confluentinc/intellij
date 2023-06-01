package com.jetbrains.bigdatatools.kafka.settings

import com.jetbrains.bigdatatools.core.constants.BdtPluginType
import com.jetbrains.bigdatatools.core.settings.connections.ConnectionGroup
import com.jetbrains.bigdatatools.core.settings.connections.ConnectionSettingProvider

class KafkaSettingsProvider : ConnectionSettingProvider {
  override val pluginType: BdtPluginType = BdtPluginType.KAFKA

  override fun createConnectionGroups(): List<ConnectionGroup> = listOf(KafkaConnectionGroup())
}