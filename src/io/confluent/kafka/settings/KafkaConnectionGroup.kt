package io.confluent.kafka.settings

import io.confluent.kafka.icons.BigdatatoolsKafkaIcons
import io.confluent.kafka.core.constants.BdtConnectionType
import io.confluent.kafka.core.settings.connections.BrokerConnectionGroup
import io.confluent.kafka.core.settings.connections.ConnectionFactory
import io.confluent.kafka.rfs.KafkaConnectionData

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