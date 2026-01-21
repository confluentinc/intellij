package io.confluent.intellijplugin.toolwindow.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import io.confluent.intellijplugin.core.monitoring.toolwindow.MainTreeController.Companion.dataManager
import io.confluent.intellijplugin.core.monitoring.toolwindow.MainTreeController.Companion.rfsPath
import io.confluent.intellijplugin.data.KafkaDataManager
import io.confluent.intellijplugin.rfs.KafkaDriver.Companion.isTopicFolder

class ClearTopicAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val rfsPath = e.rfsPath ?: return
        val dataManager = e.dataManager as KafkaDataManager

        dataManager.clearTopic(rfsPath.name)
    }

    override fun update(e: AnActionEvent) {
        val rfsPath = e.rfsPath
        // Only show for Kafka direct connections (CCloud doesn't support clear via REST API)
        e.presentation.isEnabledAndVisible =
            e.dataManager is KafkaDataManager && rfsPath?.parent?.isTopicFolder == true
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}