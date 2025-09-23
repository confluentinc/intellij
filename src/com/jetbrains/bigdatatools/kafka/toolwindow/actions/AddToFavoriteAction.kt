package io.confluent.kafka.toolwindow.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.project.DumbAwareToggleAction
import io.confluent.kafka.core.monitoring.toolwindow.MainTreeController.Companion.dataManager
import io.confluent.kafka.core.monitoring.toolwindow.MainTreeController.Companion.rfsPath
import io.confluent.kafka.core.rfs.driver.RfsPath
import io.confluent.kafka.data.KafkaDataManager
import io.confluent.kafka.rfs.KafkaDriver.Companion.isConsumers
import io.confluent.kafka.rfs.KafkaDriver.Companion.isSchemas
import io.confluent.kafka.rfs.KafkaDriver.Companion.isTopicFolder
import io.confluent.kafka.toolwindow.config.KafkaToolWindowSettings
import io.confluent.kafka.util.KafkaMessagesBundle

class AddToFavoriteAction : DumbAwareToggleAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isSelected(e: AnActionEvent): Boolean {
    val rfsPath = e.rfsPath ?: return false
    val dataManager = e.dataManager as? KafkaDataManager ?: return false

    val config = KafkaToolWindowSettings.getInstance().getOrCreateConfig(dataManager.connectionId)
    return when {
      rfsPath.isTopic() -> config.topicsPined.contains(rfsPath.name)
      rfsPath.isSchema() -> config.schemasPined.contains(rfsPath.name)
      rfsPath.isConsumerGroup() -> config.consumerGroupPined.contains(rfsPath.name)
      else -> false
    }
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val rfsPath = e.rfsPath ?: return
    val dataManager = e.dataManager as? KafkaDataManager ?: return

    when {
      rfsPath.isTopic() -> dataManager.updatePinedTopics(rfsPath.name, state)
      rfsPath.isSchema() -> dataManager.updatePinedSchemas(rfsPath.name, state)
      rfsPath.isConsumerGroup() -> dataManager.updatePinedConsumerGroups(rfsPath.name, state)
      else -> Unit
    }
  }

  override fun update(e: AnActionEvent) {
    val rfsPath = e.rfsPath
    val presentation = e.presentation
    presentation.isEnabledAndVisible = e.dataManager != null &&
                                       (rfsPath?.isTopic() == true || rfsPath?.isSchema() == true || rfsPath?.isConsumerGroup() == true)

    val selected = isSelected(e)
    // not selected icons in the context menu
    if (!e.isFromContextMenu) {
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
  private fun RfsPath.isConsumerGroup() = this.parent?.isConsumers == true
}