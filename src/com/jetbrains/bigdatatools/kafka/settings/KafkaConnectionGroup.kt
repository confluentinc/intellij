package com.jetbrains.bigdatatools.kafka.settings

import com.jetbrains.bigdatatools.common.constants.BdtConnectionType
import com.jetbrains.bigdatatools.common.settings.connections.ConnectionFactory
import com.jetbrains.bigdatatools.common.settings.connections.MonitoringConnectionGroup
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import icons.BigdatatoolsKafkaIcons

class KafkaConnectionGroup : ConnectionFactory<KafkaConnectionData>(
  id = BdtConnectionType.KAFKA.id,
  name = BdtConnectionType.KAFKA.connName,
  icon = BigdatatoolsKafkaIcons.Kafka,
  parentGroupId = MonitoringConnectionGroup.GROUP_ID
) {
  override fun newData() = KafkaConnectionData().apply {
    name = BdtConnectionType.KAFKA.connName
    uri = "127.0.0.1:9092"
  }
}