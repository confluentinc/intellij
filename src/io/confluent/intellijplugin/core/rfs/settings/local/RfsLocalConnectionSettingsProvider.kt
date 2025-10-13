package io.confluent.intellijplugin.core.rfs.settings.local

import io.confluent.intellijplugin.core.constants.BdtConnectionType
import io.confluent.intellijplugin.core.rfs.driver.local.LocalDriver
import io.confluent.intellijplugin.core.settings.connections.ConnectionFactory
import io.confluent.intellijplugin.core.settings.connections.FileSystemConnectionGroup
import io.confluent.intellijplugin.util.KafkaMessagesBundle

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