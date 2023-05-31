package com.jetbrains.bigdatatools.kafka.toolwindow.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver.Companion.isTopicFolder
import com.jetbrains.bigdatatools.kafka.toolwindow.controllers.KafkaMainController.Companion.dataManager
import com.jetbrains.bigdatatools.kafka.toolwindow.controllers.KafkaMainController.Companion.rfsPath

class ClearTopicAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val rfsPath = e.rfsPath ?: return
    val dataManager = e.dataManager

    dataManager.clearTopic(rfsPath.name)
  }

  override fun update(e: AnActionEvent) {
    val rfsPath = e.rfsPath
    e.presentation.isEnabledAndVisible = rfsPath?.parent?.isTopicFolder == true
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}