package io.confluent.intellijplugin.toolwindow.controllers

import io.confluent.intellijplugin.icons.BigdatatoolsKafkaIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import io.confluent.intellijplugin.core.monitoring.toolwindow.DetailsTableMonitoringController
import io.confluent.intellijplugin.data.TopicDetailDataProvider
import io.confluent.intellijplugin.model.BdtTopicPartition
import io.confluent.intellijplugin.toolwindow.config.KafkaToolWindowSettings
import io.confluent.intellijplugin.util.KafkaMessagesBundle

class TopicPartitionsController(private val dataManager: TopicDetailDataProvider) :
    DetailsTableMonitoringController<BdtTopicPartition, String>() {
    private val clearPartition = object : DumbAwareAction(
        KafkaMessagesBundle.message("action.kafka.ClearPartition.text"),
        null,
        BigdatatoolsKafkaIcons.ClearOutputs
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            val selectedRows = dataTable.selectedRows

            val selectedPartitions = selectedRows.map {
                dataTable.getDataAt(it)
            }.mapNotNull { it }

            dataManager.clearPartitions(selectedPartitions)
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = getSelectedItem() != null
            e.presentation.isVisible = dataManager.supportsClearPartitions()
        }

        override fun getActionUpdateThread() = ActionUpdateThread.BGT
    }

    init {
        init()
    }

    override fun getAdditionalContextActions(): List<AnAction> {
        return if (dataManager.supportsClearPartitions()) {
            listOf(clearPartition)
        } else {
            emptyList()
        }
    }

    override fun getColumnSettings() = KafkaToolWindowSettings.getInstance().topicPartitionsColumnSettings

    override fun getRenderableColumns() = BdtTopicPartition.renderableColumns

    override fun getDataModel() = selectedId?.let { dataManager.topicPartitionsModels[it] }

    override fun showColumnFilter(): Boolean = false
}