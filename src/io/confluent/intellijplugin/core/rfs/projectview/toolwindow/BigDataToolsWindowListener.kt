package io.confluent.intellijplugin.core.rfs.projectview.toolwindow

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import io.confluent.intellijplugin.core.rfs.settings.RemoteFsDriverProvider
import io.confluent.intellijplugin.core.settings.ConnectionSettingsListener
import io.confluent.intellijplugin.core.settings.ModificationKey
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import io.confluent.intellijplugin.core.util.invokeLater

class BigDataToolsWindowListener(val controller: BigDataToolWindowController) : ConnectionSettingsListener {
  override fun onConnectionAdded(project: Project?, newConnectionData: ConnectionData) {
    if (shouldBeIgnored(newConnectionData, project)) return

    val panel = getPanel()
    runInEdt {
      panel.updateRoots()
    }

    controller.createFileViewerEditor(newConnectionData)
  }

  override fun onConnectionRemoved(project: Project?, removedConnectionData: ConnectionData) {
    if (shouldBeIgnored(removedConnectionData, project)) return
    val container = getPanel()
    invokeLater {
      container.updateRoots()
    }

    controller.closeFileViewerEditors(removedConnectionData)
  }

  override fun onConnectionModified(project: Project?, connectionData: ConnectionData, modified: Collection<ModificationKey>) {
    if (shouldBeIgnored(connectionData, project)) return

    val panel = getPanel()
    invokeLater {
      panel.updateRoots()
    }

    controller.closeFileViewerEditors(connectionData)
  }

  private fun shouldBeIgnored(connectionData: ConnectionData, project: Project?): Boolean {
    val isDriverProvider = connectionData is RemoteFsDriverProvider
    val isForCorrectProject = project == null || project == controller.project
    return !isDriverProvider || !isForCorrectProject
  }

  private fun getPanel() = controller.getMainPane()
}