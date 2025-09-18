package com.jetbrains.bigdatatools.kafka.core.settings.connections

import com.jetbrains.bigdatatools.kafka.core.constants.BdtPluginType

class GeneralConnectionSettingsProvider : ConnectionSettingProvider {
  override val pluginType: BdtPluginType = BdtPluginType.KAFKA

  override fun createConnectionGroups(): List<ConnectionGroup> = listOf(BrokerConnectionGroup())
}