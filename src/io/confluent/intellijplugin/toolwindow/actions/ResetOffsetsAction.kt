package io.confluent.intellijplugin.toolwindow.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import io.confluent.intellijplugin.core.monitoring.toolwindow.MainTreeController.Companion.dataManager
import io.confluent.intellijplugin.core.monitoring.toolwindow.MainTreeController.Companion.rfsPath
import io.confluent.intellijplugin.data.KafkaDataManager
import io.confluent.intellijplugin.rfs.KafkaDriver.Companion.isConsumers
import io.confluent.intellijplugin.util.KafkaConsumerGroupChangeOffsetProcess

class ResetOffsetsAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val rfsPath = e.rfsPath ?: return
        val dataManager = e.dataManager as KafkaDataManager

        KafkaConsumerGroupChangeOffsetProcess(e.project ?: return, dataManager, rfsPath.name).showAndUpdate()
    }

    override fun update(e: AnActionEvent) {
        val rfsPath = e.rfsPath
        e.presentation.isEnabledAndVisible = e.dataManager != null && rfsPath?.parent?.isConsumers == true
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}