package io.confluent.intellijplugin.core.monitoring.toolwindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import io.confluent.intellijplugin.core.constants.BdtConnectionType
import io.confluent.intellijplugin.core.constants.BdtPlugins
import io.confluent.intellijplugin.core.settings.manager.RfsConnectionDataManager

abstract class MonitoringToolWindowFactory : ToolWindowFactory, DumbAware {
    protected abstract val toolWindowId: String
    protected abstract val connectionType: BdtConnectionType
    protected abstract val title: String

    override fun shouldBeAvailable(project: Project): Boolean {
        // We will show ToolWindow stripe button if:
        // 1. Plugin installed
        // 2. We have any connection of this connectionType configured.
        return BdtPlugins.isPluginInstalled(connectionType.pluginType) &&
                !RfsConnectionDataManager.instance?.getConnectionsByGroupId(connectionType.id, project).isNullOrEmpty()
    }

    override fun init(toolWindow: ToolWindow) {
        toolWindow.stripeTitle = title

        val toolWindowActivator = ToolWindowActivator(toolWindowId, connectionType.id)
        Disposer.register(toolWindow.disposable, toolWindowActivator)
    }
}