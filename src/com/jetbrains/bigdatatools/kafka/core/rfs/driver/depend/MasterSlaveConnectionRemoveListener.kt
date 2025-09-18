package com.jetbrains.bigdatatools.kafka.core.rfs.driver.depend

import com.intellij.openapi.project.Project
import com.jetbrains.bigdatatools.kafka.core.settings.CommonSettingsKeys
import com.jetbrains.bigdatatools.kafka.core.settings.ConnectionSettingsListener
import com.jetbrains.bigdatatools.kafka.core.settings.ExtendedConnectionData
import com.jetbrains.bigdatatools.kafka.core.settings.ModificationKey
import com.jetbrains.bigdatatools.kafka.core.settings.connections.ConnectionData
import com.jetbrains.bigdatatools.kafka.core.settings.manager.RfsConnectionDataManager
import com.jetbrains.bigdatatools.kafka.core.util.ConnectionUtil
import com.jetbrains.bigdatatools.kafka.core.util.invokeLater

class MasterSlaveConnectionRemoveListener : ConnectionSettingsListener {
  override fun onConnectionRemoved(project: Project?, removedConnectionData: ConnectionData) {
    if (removedConnectionData is ExtendedConnectionData)
      return

    removedConnectionData.sourceConnection?.let {
      val masterConnectionData = RfsConnectionDataManager.instance?.getConnectionById(project, it) as? MasterConnectionData<*>
      masterConnectionData?.removeSlave(setOf(removedConnectionData.innerId))
    }

    if (removedConnectionData !is MasterConnectionData<*>)
      return
    val dependConnections = removedConnectionData.getDependConnections(project)
    if (dependConnections.isEmpty())
      return

    invokeLater {
      dependConnections.forEach {
        RfsConnectionDataManager.instance?.removeConnection(project, it)
      }
    }
  }

  override fun onConnectionModified(project: Project?, connectionData: ConnectionData, modified: Collection<ModificationKey>) {
    if (connectionData is ExtendedConnectionData)
      return

    if (connectionData.isEnabled || connectionData !is MasterConnectionData<*> || CommonSettingsKeys.ENABLED_KEY !in modified)
      return
    val dependConnections = connectionData.getDependConnections(project)
    if (dependConnections.isEmpty())
      return

    invokeLater {
      dependConnections.forEach {
        ConnectionUtil.modifyConnection(project, it, false)
      }
    }
  }

  override fun getId(): String = "MasterConnectionSettingsListener"
}