package com.jetbrains.bigdatatools.kafka.toolwindow.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver.Companion.isTopicFolder
import com.jetbrains.bigdatatools.kafka.toolwindow.controllers.KafkaMainController.Companion.dataManager
import com.jetbrains.bigdatatools.kafka.toolwindow.controllers.KafkaMainController.Companion.rfsPath
import com.jetbrains.bigdatatools.kafka.util.KafkaDialogFactory

class CreateTopicAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val dataManager = e.dataManager
    KafkaDialogFactory.showCreateTopicDialog(dataManager)
  }

  override fun update(e: AnActionEvent) {
    val rfsPath = e.rfsPath
    e.presentation.isEnabledAndVisible = rfsPath?.parent?.isTopicFolder == true || rfsPath?.isTopicFolder == true
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}