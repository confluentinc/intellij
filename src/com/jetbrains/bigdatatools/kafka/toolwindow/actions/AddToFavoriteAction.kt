package com.jetbrains.bigdatatools.kafka.toolwindow.actions

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.project.DumbAwareToggleAction
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.MainTreeController.Companion.dataManager
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.MainTreeController.Companion.rfsPath
import com.jetbrains.bigdatatools.common.rfs.driver.RfsPath
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver.Companion.isSchemas
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver.Companion.isTopicFolder
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaClusterConfig
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

class AddToFavoriteAction : DumbAwareToggleAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isSelected(e: AnActionEvent): Boolean {
    val rfsPath = e.rfsPath ?: return false
    val dataManager = e.dataManager as? KafkaDataManager ?: return false

    val config = KafkaToolWindowSettings.getInstance().getOrCreateConfig(dataManager.connectionId)
    return if (rfsPath.isTopic())
      config.topicsPined.contains(rfsPath.name)
    else config.schemasPined.contains(rfsPath.name)
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val rfsPath = e.rfsPath ?: return
    val dataManager = e.dataManager as? KafkaDataManager ?: return

    val config = KafkaToolWindowSettings.getInstance().getOrCreateConfig(dataManager.connectionId)
    if (rfsPath.isTopic())
      updateTopics(dataManager, config, rfsPath, state)
    else updateSchemas(dataManager, config, rfsPath, state)
  }

  private fun updateTopics(dataManager: KafkaDataManager, config: KafkaClusterConfig, rfsPath: RfsPath, state: Boolean) {
    if (state) {
      config.topicsPined += rfsPath.name
    }
    else {
      config.topicsPined -= rfsPath.name
    }
    dataManager.getTopicByName(rfsPath.name)?.let { it.isFavorite = state }
    dataManager.updater.invokeRefreshModel(dataManager.topicModel)
  }

  private fun updateSchemas(dataManager: KafkaDataManager, config: KafkaClusterConfig, rfsPath: RfsPath, state: Boolean) {
    if (state) {
      config.schemasPined += rfsPath.name
    }
    else {
      config.schemasPined -= rfsPath.name
    }
    dataManager.getSchemaByName(rfsPath.name)?.let { it.isFavorite = state }
    dataManager.schemaRegistryModel?.let { dataManager.updater.invokeRefreshModel(it) }
  }

  override fun update(e: AnActionEvent) {
    val rfsPath = e.rfsPath
    val presentation = e.presentation
    presentation.isEnabledAndVisible = e.dataManager != null &&
                                       (rfsPath?.isTopic() == true || rfsPath?.isSchema() == true)

    val selected = isSelected(e)
    // not selected icons in the context menu
    if (!ActionPlaces.isPopupPlace(e.place)) {
      Toggleable.setSelected(presentation, selected)
    }

    if (selected) {
      presentation.text = KafkaMessagesBundle.message("action.Kafka.RemoveFromFavoriteAction.text")
    }
    else {
      presentation.text = KafkaMessagesBundle.message("action.Kafka.AddToFavoriteAction.text")
    }
  }

  private fun RfsPath.isTopic() = this.parent?.isTopicFolder == true
  private fun RfsPath.isSchema() = this.parent?.isSchemas == true
}