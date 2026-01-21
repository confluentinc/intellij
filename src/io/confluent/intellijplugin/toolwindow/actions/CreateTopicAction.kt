package io.confluent.intellijplugin.toolwindow.actions

import com.intellij.ide.actions.NewElementAction
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import io.confluent.intellijplugin.core.monitoring.toolwindow.MainTreeController.Companion.dataManager
import io.confluent.intellijplugin.core.monitoring.toolwindow.MainTreeController.Companion.rfsPath
import io.confluent.intellijplugin.data.KafkaDataManager
import io.confluent.intellijplugin.rfs.KafkaDriver.Companion.isTopicFolder
import io.confluent.intellijplugin.util.KafkaDialogFactory

class CreateTopicAction : NewElementAction(), ActionPromoter, DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val dataManager = e.dataManager as KafkaDataManager
        KafkaDialogFactory.showCreateTopicDialog(dataManager)
    }

    override fun update(e: AnActionEvent) {
        val rfsPath = e.rfsPath
        e.presentation.isEnabledAndVisible =
            e.dataManager is io.confluent.intellijplugin.data.TopicOperations &&
            (rfsPath?.parent?.isTopicFolder == true || rfsPath?.isTopicFolder == true)
    }

    override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction> {
        return listOf(this)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}