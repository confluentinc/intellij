package io.confluent.intellijplugin.toolwindow.actions

import com.intellij.ide.actions.NewElementAction
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import io.confluent.intellijplugin.core.monitoring.toolwindow.MainTreeController.Companion.dataManager
import io.confluent.intellijplugin.core.monitoring.toolwindow.MainTreeController.Companion.rfsPath
import io.confluent.intellijplugin.data.TopicOperations
import io.confluent.intellijplugin.rfs.KafkaDriver.Companion.isTopicFolder
import io.confluent.intellijplugin.util.KafkaDialogFactory

class CreateTopicAction : NewElementAction(), ActionPromoter, DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val dataManager = e.dataManager as TopicOperations
        KafkaDialogFactory.showCreateTopicDialog(dataManager)
    }

    override fun update(e: AnActionEvent) {
        val rfsPath = e.rfsPath
        val dataManager = e.dataManager

        // For TopicOperations, enable the action if:
        // 1. For Kafka: we're on the Topics folder or a topic item
        // 2. For Confluent Cloud: we're on a cluster (1 element) or a topic (2 elements)
        val isValidPath = when {
            rfsPath == null -> false
            // Kafka paths: check if parent is topic folder or path itself is topic folder
            rfsPath.parent?.isTopicFolder == true || rfsPath.isTopicFolder -> true
            // Confluent Cloud paths: cluster (1 element) or topic (2 elements)
            rfsPath.elements.size in 1..2 -> true
            else -> false
        }

        e.presentation.isEnabledAndVisible =
            dataManager is io.confluent.intellijplugin.data.TopicOperations && isValidPath
    }

    override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction> {
        return listOf(this)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}