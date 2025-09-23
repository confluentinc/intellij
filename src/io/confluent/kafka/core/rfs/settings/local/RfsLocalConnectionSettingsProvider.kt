package io.confluent.kafka.core.rfs.settings.local

import io.confluent.kafka.core.constants.BdtConnectionType
import io.confluent.kafka.core.rfs.driver.local.LocalDriver
import io.confluent.kafka.core.settings.connections.ConnectionFactory
import io.confluent.kafka.core.settings.connections.FileSystemConnectionGroup
import io.confluent.kafka.util.KafkaMessagesBundle

class RfsLocalConnectionGroup : ConnectionFactory<RfsLocalConnectionData>(
  id = BdtConnectionType.LOCAL.id,
  name = BdtConnectionType.LOCAL.connName,
  icon = LocalDriver.driverIcon(),
  parentGroupId = FileSystemConnectionGroup.GROUP_ID
) {
  override fun newData() = RfsLocalConnectionData().apply {
    name = BdtConnectionType.LOCAL.connName
  }

  companion object {
    val LOCAL_CONNECTION_NAME = KafkaMessagesBundle.message("rfs.local.connection.name")
  }
}