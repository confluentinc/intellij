package io.confluent.intellijplugin.toolwindow.actions

import com.intellij.CommonBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import io.confluent.intellijplugin.core.monitoring.data.MonitoringDataManager
import io.confluent.intellijplugin.core.monitoring.toolwindow.MainTreeController.Companion.dataManager
import io.confluent.intellijplugin.core.monitoring.toolwindow.MainTreeController.Companion.navigableController
import io.confluent.intellijplugin.core.monitoring.toolwindow.MainTreeController.Companion.rfsPath
import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import io.confluent.intellijplugin.core.rfs.util.RfsNotificationUtils
import io.confluent.intellijplugin.core.util.invokeLater
import io.confluent.intellijplugin.data.TopicOperations
import io.confluent.intellijplugin.rfs.KafkaDriver.Companion.isTopicFolder
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import kotlinx.coroutines.launch

class DeleteTopicAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val rfsPath = e.rfsPath ?: return
        val dataManager = e.dataManager as? TopicOperations ?: return
        val monitoringDataManager = dataManager as? MonitoringDataManager ?: return
        val controller = e.navigableController

        val topicName = rfsPath.name

        // Show confirmation dialog
        val msg = KafkaMessagesBundle.message("action.delete.topic.single.message", topicName)
        val res = Messages.showOkCancelDialog(
            monitoringDataManager.project,
            msg,
            KafkaMessagesBundle.message("action.delete.topic.title"),
            CommonBundle.getOkButtonText(),
            CommonBundle.getCancelButtonText(),
            Messages.getQuestionIcon()
        )
        if (res != Messages.OK) {
            return
        }

        // Launch coroutine in driver scope with error handling
        monitoringDataManager.driver.coroutineScope.launch {
            try {
                val result = dataManager.deleteTopic(listOf(topicName))

                result.onSuccess {
                    // Navigate to parent (cluster) to clear detail view and prevent error after delete a topic
                    controller?.let {
                        invokeLater {
                            val parentPath = rfsPath.parent ?: RfsPath(listOf(rfsPath.elements.first()), true)
                            it.open(parentPath)
                        }
                    }
                }

                // Show error notification if deletion failed
                result.onFailure { exception ->
                    RfsNotificationUtils.showExceptionMessage(monitoringDataManager.project, exception)
                }
            } catch (t: Throwable) {
                RfsNotificationUtils.showExceptionMessage(monitoringDataManager.project, t)
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val rfsPath = e.rfsPath
        val dataManager = e.dataManager

        // For TopicOperations, enable the action if:
        // 1. For Kafka: we're on a topic item (parent is Topics folder)
        // 2. For Confluent Cloud: we're on a topic (2 elements: clusterId/topicName)
        val isValidPath = when {
            rfsPath == null -> false
            // Kafka paths: check if parent is topic folder (meaning we're on a specific topic)
            rfsPath.parent?.isTopicFolder == true -> true
            // Confluent Cloud paths: topic (2 elements: clusterId/topicName)
            rfsPath.elements.size == 2 -> true
            else -> false
        }

        e.presentation.isEnabledAndVisible =
            dataManager is io.confluent.intellijplugin.data.TopicOperations && isValidPath
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

}