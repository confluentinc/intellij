package com.jetbrains.bigdatatools.kafka.toolwindow.actions

import com.intellij.ide.actions.NewElementAction
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.MainTreeController.Companion.dataManager
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.MainTreeController.Companion.rfsPath
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver.Companion.isTopicFolder
import com.jetbrains.bigdatatools.kafka.util.KafkaDialogFactory

class CreateTopicAction : NewElementAction(), ActionPromoter, DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val dataManager = e.dataManager as KafkaDataManager
    KafkaDialogFactory.showCreateTopicDialog(dataManager)
  }

  override fun update(e: AnActionEvent) {
    val rfsPath = e.rfsPath
    e.presentation.isEnabledAndVisible = e.dataManager != null && rfsPath?.parent?.isTopicFolder == true || rfsPath?.isTopicFolder == true
  }

  override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction> {
    return listOf(this)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}