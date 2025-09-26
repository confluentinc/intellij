package io.confluent.intellijplugin.core.rfs.driver.depend

import com.intellij.openapi.project.Project
import io.confluent.intellijplugin.core.settings.CommonSettingsKeys
import io.confluent.intellijplugin.core.settings.ConnectionSettingsListener
import io.confluent.intellijplugin.core.settings.ExtendedConnectionData
import io.confluent.intellijplugin.core.settings.ModificationKey
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import io.confluent.intellijplugin.core.settings.manager.RfsConnectionDataManager
import io.confluent.intellijplugin.core.util.ConnectionUtil
import io.confluent.intellijplugin.core.util.invokeLater

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