package com.jetbrains.bigdatatools.kafka.toolwindow.controllers

import com.intellij.bigdatatools.kafka.icons.BigdatatoolsKafkaIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.bigdatatools.kafka.core.monitoring.toolwindow.DetailsTableMonitoringController
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.model.BdtTopicPartition
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

class TopicPartitionsController(private val dataManager: KafkaDataManager) : DetailsTableMonitoringController<BdtTopicPartition, String>() {
  private val clearPartition = object : DumbAwareAction(KafkaMessagesBundle.message("action.kafka.ClearPartition.text"),
                                                        null,
                                                        BigdatatoolsKafkaIcons.ClearOutputs) {
    override fun actionPerformed(e: AnActionEvent) {
      val selectedRows = dataTable.selectedRows

      val selectedPartitions = selectedRows.map {
        dataTable.getDataAt(it)
      }.mapNotNull { it }

      dataManager.clearPartitions(selectedPartitions)
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = dataManager.client.isConnected() && getSelectedItem() != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
  }

  init {
    init()
  }

  override fun getAdditionalContextActions(): List<AnAction> = listOf(clearPartition)

  override fun getColumnSettings() = KafkaToolWindowSettings.getInstance().topicPartitionsColumnSettings

  override fun getRenderableColumns() = BdtTopicPartition.renderableColumns

  override fun getDataModel() = selectedId?.let { dataManager.topicPartitionsModels[it] }

  override fun showColumnFilter(): Boolean = false
}