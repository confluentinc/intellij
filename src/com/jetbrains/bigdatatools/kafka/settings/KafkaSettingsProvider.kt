package com.jetbrains.bigdatatools.kafka.settings

import com.jetbrains.bigdatatools.settings.connections.ConnectionGroup
import com.jetbrains.bigdatatools.settings.connections.ConnectionSettingProvider

class KafkaSettingsProvider : ConnectionSettingProvider {
  override fun createConnectionGroups(): List<ConnectionGroup> = listOf(KafkaConnectionGroup())
}