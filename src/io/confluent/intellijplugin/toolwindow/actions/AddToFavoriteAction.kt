package io.confluent.intellijplugin.toolwindow.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.project.DumbAwareToggleAction
import io.confluent.intellijplugin.core.monitoring.toolwindow.MainTreeController.Companion.dataManager
import io.confluent.intellijplugin.core.monitoring.toolwindow.MainTreeController.Companion.rfsPath
import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import io.confluent.intellijplugin.data.BaseClusterDataManager
import io.confluent.intellijplugin.rfs.KafkaDriver.Companion.isConsumers
import io.confluent.intellijplugin.rfs.KafkaDriver.Companion.isSchemas
import io.confluent.intellijplugin.rfs.KafkaDriver.Companion.isTopicFolder
import io.confluent.intellijplugin.toolwindow.config.KafkaToolWindowSettings
import io.confluent.intellijplugin.util.KafkaMessagesBundle

class AddToFavoriteAction : DumbAwareToggleAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun isSelected(e: AnActionEvent): Boolean {
        val rfsPath = e.rfsPath ?: return false
        val dataManager = e.dataManager as? BaseClusterDataManager ?: return false

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
        val dataManager = e.dataManager as? BaseClusterDataManager ?: return

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
        } else {
            presentation.text = KafkaMessagesBundle.message("action.Kafka.AddToFavoriteAction.text")
        }
    }

    private fun RfsPath.isTopic(): Boolean {
        val parentPath = this.parent ?: return false
        // Kafka: parent is "Topics" folder
        // CCloud: parent is cluster ID (lkc-*)
        return parentPath.name == "Topics" ||
               (parentPath.elements.size == 1 && parentPath.name.startsWith("lkc-") && parentPath.isDirectory)
    }

    private fun RfsPath.isSchema(): Boolean {
        val parentPath = this.parent ?: return false
        // Kafka: parent is "Schema Registry" folder
        // CCloud: parent is schema registry ID (lsrc-*)
        return parentPath.name == "Schema Registry" ||
               (parentPath.elements.size == 1 && parentPath.name.startsWith("lsrc-") && parentPath.isDirectory)
    }

    private fun RfsPath.isConsumerGroup(): Boolean {
        val parentPath = this.parent ?: return false
        // Kafka: parent is "Consumer Groups" folder
        // CCloud: Not yet supported (TODO)
        return parentPath.name == "Consumer Groups"
    }
}