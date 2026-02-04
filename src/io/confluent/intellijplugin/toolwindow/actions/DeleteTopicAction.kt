package io.confluent.intellijplugin.toolwindow.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import io.confluent.intellijplugin.core.monitoring.toolwindow.MainTreeController.Companion.dataManager
import io.confluent.intellijplugin.core.monitoring.toolwindow.MainTreeController.Companion.rfsPath
import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import io.confluent.intellijplugin.data.BaseClusterDataManager

class DeleteTopicAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val rfsPath = e.rfsPath ?: return
        (e.dataManager as BaseClusterDataManager).deleteTopicWithConfirmation(listOf(rfsPath.name))
    }

    override fun update(e: AnActionEvent) {
        val rfsPath = e.rfsPath
        e.presentation.isEnabledAndVisible =
            e.dataManager is BaseClusterDataManager && rfsPath?.isUnderTopicFolder() == true
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private fun RfsPath.isUnderTopicFolder(): Boolean {
        val parentPath = this.parent ?: return false
        // Kafka: parent is "Topics" folder
        // CCloud: parent is cluster ID (lkc-*)
        return parentPath.name == "Topics" ||
               (parentPath.elements.size == 1 && parentPath.name.startsWith("lkc-") && parentPath.isDirectory)
    }
}