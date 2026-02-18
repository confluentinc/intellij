package io.confluent.intellijplugin.toolwindow.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import io.confluent.intellijplugin.core.monitoring.toolwindow.MainTreeController.Companion.dataManager
import io.confluent.intellijplugin.core.monitoring.toolwindow.MainTreeController.Companion.rfsPath
import io.confluent.intellijplugin.core.table.ClipboardUtils
import io.confluent.intellijplugin.data.CCloudClusterDataManager
import io.confluent.intellijplugin.rfs.ConfluentDriver.Companion.getClusterId

/**
 * Action to copy cluster ID to clipboard from context menu.
 * Visible only when a Confluent Cloud cluster node is selected in the tree.
 */
class CopyClusterIdAction : AnAction(), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val rfsPath = e.rfsPath ?: return
        val clusterId = rfsPath.getClusterId() ?: return
        ClipboardUtils.setStringContent(clusterId)
    }

    override fun update(e: AnActionEvent) {
        val rfsPath = e.rfsPath

        e.presentation.isEnabledAndVisible =
            e.dataManager is CCloudClusterDataManager &&
            rfsPath != null &&
            rfsPath.elements.size == 1 &&
            rfsPath.name.startsWith("lkc-")
    }
}
