package com.jetbrains.bigdatatools.kafka.settings

import com.jetbrains.bigdatatools.common.settings.connections.ConnectionFactory
import com.jetbrains.bigdatatools.common.settings.connections.MonitoringConnectionGroup
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import icons.BigdatatoolsKafkaIcons

class KafkaConnectionGroup : ConnectionFactory<KafkaConnectionData>(
  id = KafkaSettingsIds.GROUP_ID,
  name = KafkaMessagesBundle.message("settings.group.name"),
  icon = BigdatatoolsKafkaIcons.Kafka,
  parentGroupId = MonitoringConnectionGroup.GROUP_ID
) {
  override fun newData() = KafkaConnectionData().apply {
    name = "Kafka connection"
    uri = "127.0.0.1:9092"
  }
}