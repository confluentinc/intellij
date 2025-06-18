package com.jetbrains.bigdatatools.kafka.core.rfs.settings.local

import com.jetbrains.bigdatatools.kafka.core.constants.BdtConnectionType
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.local.LocalDriver
import com.jetbrains.bigdatatools.kafka.core.settings.connections.ConnectionFactory
import com.jetbrains.bigdatatools.kafka.core.settings.connections.FileSystemConnectionGroup
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

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