package io.confluent.kafka.core.settings

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import io.confluent.kafka.core.constants.BdtConnectionType
import io.confluent.kafka.core.constants.BdtPlugins
import io.confluent.kafka.core.settings.manager.RfsConnectionDataManager

class AdditionalPluginLoadingListener : DynamicPluginListener {
  override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
    if (!BdtPlugins.ALL_PLUGINS.contains(pluginDescriptor.pluginId.idString)) return

    tryToLoadConnections(GlobalConnectionSettings.getInstance(), null)

    ProjectManager.getInstance().openProjects.forEach { project ->
      tryToLoadConnections(LocalConnectionSettings.getInstance(project), project)
    }
  }

  override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
    if (!BdtPlugins.ALL_PLUGINS.contains(pluginDescriptor.pluginId.idString)) return

    tryToUnloadConnections(GlobalConnectionSettings.getInstance(), null, pluginDescriptor.pluginId.idString)

    ProjectManager.getInstance().openProjects.forEach { project ->
      tryToUnloadConnections(LocalConnectionSettings.getInstance(project), project, pluginDescriptor.pluginId.idString)
    }
  }

  private fun tryToLoadConnections(storage: ConnectionSettingsBase, project: Project?) {
    val connections = storage.getConnections()
    if (!connections.any { it is ExtendedConnectionData }) return

    for (c in connections) {
      if (c !is ExtendedConnectionData) continue
      val t = ConnectionSettingsBase.unpackData(c)

      if (t !is ExtendedConnectionData) {
        RfsConnectionDataManager.instance?.removeConnection(project, c)
        RfsConnectionDataManager.instance?.addConnection(project, t)
      }
    }
  }

  private fun tryToUnloadConnections(storage: ConnectionSettingsBase, project: Project?, pluginId: String) {
    val connections = storage.getNotParsedConnections()
    val connectionForUnserialize = connections.filter {
      BdtConnectionType.getForId(it.groupId)?.pluginType?.pluginId == pluginId
    }

    connectionForUnserialize.forEach {
      //DriverManager.getDriverById(project,it.innerId)?.let { Disposer.dispose(it) }
      val packData = ConnectionSettingsBase.packData(it)
      RfsConnectionDataManager.instance?.removeConnectionKeepingCredentials(project, it)
      RfsConnectionDataManager.instance?.addConnection(project, packData)
    }
  }
}