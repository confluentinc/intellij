package com.jetbrains.bigdatatools.kafka.toolwindow.controllers

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.model.TopicConfig
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.DetailsTableMonitoringController

class TopicConfigsController(val project: Project,
                             private val dataManager: KafkaDataManager) : DetailsTableMonitoringController<TopicConfig>() {
  init {
    init()
  }

  override fun getColumnSettings() = KafkaToolWindowSettings.getInstance().topicConfigsColumnSettings

  override fun getRenderableColumns() = TopicConfig.renderableColumns

  override fun getDataModel() = selectedId?.let { dataManager.getTopicConfigsModel(it) }

  override fun getAdditionalActions(): List<AnAction> {
    val settings = KafkaToolWindowSettings.getInstance()

    val showFullConfig = object : DumbAwareToggleAction(KafkaMessagesBundle.message("show.full.topic.config"),
                                                        KafkaMessagesBundle.message("show.full.topic.config.hint"),
                                                        AllIcons.Actions.ToggleVisibility) {
      override fun isSelected(e: AnActionEvent) = settings.showFullTopicConfig

      override fun getActionUpdateThread() = ActionUpdateThread.BGT

      override fun displayTextInToolbar() = false

      override fun setSelected(e: AnActionEvent, state: Boolean) {
        settings.showFullTopicConfig = state

        //Create if disposed
        selectedId?.let { dataManager.getTopicConfigsModel(it) }

        //Revalidate for all stored models
        dataManager.topicConfigsModels.values.forEach {
          it.updateData()
        }
      }
    }

    return listOf(showFullConfig)
  }
}