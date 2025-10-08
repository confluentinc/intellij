package io.confluent.intellijplugin.core.monitoring.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.ToolWindowManager
import io.confluent.intellijplugin.core.constants.BdtPlugins
import io.confluent.intellijplugin.core.settings.CommonSettingsKeys
import io.confluent.intellijplugin.core.settings.ConnectionSettingsListener
import io.confluent.intellijplugin.core.settings.ModificationKey
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import io.confluent.intellijplugin.core.settings.manager.RfsConnectionDataManager

/**
 * Opens specified ToolWindow by toolWindowId, on first added connection of required type.
 *
 * Subscribes to RfsConnectionDataManager to catch connection creation.
 */
class ToolWindowActivator(private val toolWindowId: String, private val connGroupId: String) :
    ConnectionSettingsListener, Disposable {
    init {
        RfsConnectionDataManager.instance?.addListener(this)
    }

    override fun dispose() {
        RfsConnectionDataManager.instance?.removeListener(this)
    }

    private fun showToolWindow(project: Project) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(toolWindowId) ?: return
        toolWindow.setAvailable(true) {
            toolWindow.show()
        }
    }

    override fun onConnectionAdded(project: Project?, newConnectionData: ConnectionData) {
        onConnectionsChange(newConnectionData, project)
    }

    override fun onConnectionModified(
        project: Project?,
        connectionData: ConnectionData,
        modified: Collection<ModificationKey>
    ) {
        if (CommonSettingsKeys.ENABLED_KEY in modified && connectionData.isEnabled)
            onConnectionsChange(connectionData, project)
    }

    private fun onConnectionsChange(newConnectionData: ConnectionData, project: Project?) {
        if (newConnectionData.groupId != connGroupId)
            return
        if (!newConnectionData.isEnabled)
            return
        if (!BdtPlugins.isSupportedConnectionGroup(connGroupId))
            return

        val projects = if (newConnectionData.isPerProject)
            listOfNotNull(project)
        else
            ProjectManager.getInstance().openProjects.toList()

        projects.forEach {
            showToolWindow(it)
        }
    }
}