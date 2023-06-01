package com.jetbrains.bigdatatools.kafka.settings

import com.intellij.bigdatatools.kafka.BigdatatoolsKafkaIcons
import com.jetbrains.bigdatatools.core.constants.BdtConnectionType
import com.jetbrains.bigdatatools.core.settings.connections.ConnectionFactory
import com.jetbrains.bigdatatools.core.settings.connections.MonitoringConnectionGroup
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData

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