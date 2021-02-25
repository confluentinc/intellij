package com.jetbrains.bigdatatools.kafka.settings

import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.kafka.util.KafkaIcons
import com.jetbrains.bigdatatools.settings.connections.ConnectionData
import com.jetbrains.bigdatatools.settings.connections.ConnectionGroup
import com.jetbrains.bigdatatools.settings.connections.MonitoringConnectionGroup

class KafkaConnectionGroup : ConnectionGroup(id = KafkaSettingsIds.GROUP_ID,
                                             name = KafkaSettingsIds.GROUP_NAME,
                                             icon = KafkaIcons.MAIN_ICON,
                                             parentGroupId = MonitoringConnectionGroup.GROUP_ID) {
  override fun newData(): ConnectionData = KafkaConnectionData().apply {
    name = "Kafka connection"
    uri = "127.0.0.1:9092"
    port = -1
  }
}