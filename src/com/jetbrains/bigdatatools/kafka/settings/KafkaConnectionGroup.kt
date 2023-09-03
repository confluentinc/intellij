package com.jetbrains.bigdatatools.kafka.settings

import com.intellij.bigdatatools.kafka.icons.BigdatatoolsKafkaIcons
import com.jetbrains.bigdatatools.common.constants.BdtConnectionType
import com.jetbrains.bigdatatools.common.settings.connections.BrokerConnectionGroup
import com.jetbrains.bigdatatools.common.settings.connections.ConnectionFactory
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData

class KafkaConnectionGroup : ConnectionFactory<KafkaConnectionData>(
  id = BdtConnectionType.KAFKA.id,
  name = BdtConnectionType.KAFKA.connName,
  icon = BigdatatoolsKafkaIcons.Kafka,
  parentGroupId = BrokerConnectionGroup.GROUP_ID
) {
  override fun newData() = KafkaConnectionData(version = 5).apply {
    name = BdtConnectionType.KAFKA.connName
    uri = "127.0.0.1:9092"
  }
}