package io.confluent.kafka.toolwindow.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import io.confluent.kafka.core.monitoring.toolwindow.MainTreeController.Companion.dataManager
import io.confluent.kafka.core.monitoring.toolwindow.MainTreeController.Companion.rfsPath
import io.confluent.kafka.data.KafkaDataManager
import io.confluent.kafka.rfs.KafkaDriver.Companion.isConsumers

class DeleteConsumerGroupAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val rfsPath = e.rfsPath ?: return
    val dataManager = e.dataManager as KafkaDataManager

    dataManager.deleteConsumerGroup(rfsPath.name)
  }

  override fun update(e: AnActionEvent) {
    val rfsPath = e.rfsPath
    e.presentation.isEnabledAndVisible = e.dataManager != null && rfsPath?.parent?.isConsumers == true
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}