package io.confluent.intellijplugin.toolwindow.kafka.controllers

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import io.confluent.intellijplugin.core.monitoring.toolwindow.DetailsTableMonitoringController
import io.confluent.intellijplugin.core.util.executeOnPooledThread
import io.confluent.intellijplugin.data.KafkaDataManager
import io.confluent.intellijplugin.model.TopicConfig
import io.confluent.intellijplugin.toolwindow.config.KafkaToolWindowSettings
import io.confluent.intellijplugin.util.KafkaMessagesBundle

class TopicConfigsController(
    val project: Project,
    private val dataManager: KafkaDataManager
) : DetailsTableMonitoringController<TopicConfig, String>() {
    init {
        init()
    }

    override fun getColumnSettings() = KafkaToolWindowSettings.getInstance().topicConfigsColumnSettings

    override fun getRenderableColumns() = TopicConfig.renderableColumns

    override fun getDataModel() = selectedId?.let { dataManager.topicConfigsModels[it] }

    override fun showColumnFilter(): Boolean = false

    override fun getAdditionalActions(): List<AnAction> {
        val settings = KafkaToolWindowSettings.getInstance()

        val showFullConfig = object : DumbAwareToggleAction(
            KafkaMessagesBundle.message("show.full.topic.config"),
            KafkaMessagesBundle.message("show.full.topic.config.hint"),
            AllIcons.Actions.ToggleVisibility
        ) {
            override fun isSelected(e: AnActionEvent) = settings.showFullTopicConfig

            override fun getActionUpdateThread() = ActionUpdateThread.BGT

            override fun setSelected(e: AnActionEvent, state: Boolean) {
                settings.showFullTopicConfig = state

                //Create if disposed
                selectedId?.let { dataManager.topicConfigsModels.get(it) }

                //Revalidate for all stored models

                executeOnPooledThread {
                    val modelsForRefresh = dataManager.topicConfigsModels.getModelsForRefresh()
                    dataManager.updater.invokeRefreshModels(modelsForRefresh)
                }
            }

            override fun update(e: AnActionEvent) {
                super.update(e)

                val selected = isSelected(e)
                if (selected) {
                    e.presentation.text = KafkaMessagesBundle.message("hide.full.topic.config")
                    e.presentation.description = KafkaMessagesBundle.message("hide.full.topic.config.hint")
                } else {
                    e.presentation.text = KafkaMessagesBundle.message("show.full.topic.config")
                    e.presentation.description = KafkaMessagesBundle.message("show.full.topic.config.hint")
                }
            }
        }

        return listOf(showFullConfig)
    }
}