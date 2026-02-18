package io.confluent.intellijplugin.toolwindow.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import io.confluent.intellijplugin.core.monitoring.toolwindow.MainTreeController.Companion.dataManager
import io.confluent.intellijplugin.core.monitoring.toolwindow.MainTreeController.Companion.rfsPath
import io.confluent.intellijplugin.core.table.ClipboardUtils
import io.confluent.intellijplugin.data.CCloudClusterDataManager
import io.confluent.intellijplugin.data.CCloudOrgManager
import io.confluent.intellijplugin.rfs.ConfluentDriver.Companion.getClusterId

/** Copies Confluent Cloud cluster ID to clipboard (Kafka or Schema Registry). */
class CopyClusterIdAction : AnAction(), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val rfsPath = e.rfsPath ?: return
        val clusterId = rfsPath.getClusterId() ?: return
        ClipboardUtils.setStringContent(clusterId)
    }

    override fun update(e: AnActionEvent) {
        val rfsPath = e.rfsPath
        val dataManager = e.dataManager

        // Hide from toolbar, only show in context menu
        if (!e.isFromContextMenu) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val isKafkaCluster = dataManager is CCloudClusterDataManager &&
            rfsPath != null &&
            rfsPath.elements.size == 1 &&
            rfsPath.name.startsWith("lkc-")

        val isSchemaRegistry = dataManager is CCloudOrgManager &&
            rfsPath != null &&
            rfsPath.elements.size == 1 &&
            rfsPath.name.startsWith("lsrc-")

        e.presentation.isEnabledAndVisible = isKafkaCluster || isSchemaRegistry
    }
}
