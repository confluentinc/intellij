package com.jetbrains.bigdatatools.kafka.settings

import com.jetbrains.bigdatatools.common.settings.connections.ConnectionGroup
import com.jetbrains.bigdatatools.common.settings.connections.ConnectionSettingProvider

class KafkaSettingsProvider : ConnectionSettingProvider {
  override fun createConnectionGroups(): List<ConnectionGroup> = listOf(KafkaConnectionGroup())
}