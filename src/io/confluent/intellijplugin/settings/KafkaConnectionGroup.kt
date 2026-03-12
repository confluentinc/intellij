package io.confluent.intellijplugin.settings

import io.confluent.intellijplugin.core.constants.BdtConnectionType
import io.confluent.intellijplugin.core.settings.connections.BrokerConnectionGroup
import io.confluent.intellijplugin.core.settings.connections.ConnectionFactory
import io.confluent.intellijplugin.icons.BigdatatoolsKafkaIcons
import io.confluent.intellijplugin.rfs.KafkaConnectionData

class KafkaConnectionGroup : ConnectionFactory<KafkaConnectionData>(
    id = BdtConnectionType.KAFKA.id,
    name = BdtConnectionType.KAFKA.connName,
    icon = BigdatatoolsKafkaIcons.ConfluentTab,
    parentGroupId = BrokerConnectionGroup.GROUP_ID
) {
    override fun newData() = KafkaConnectionData(version = 5).apply {
        name = BdtConnectionType.KAFKA.connName
        uri = "127.0.0.1:9092"
    }
}