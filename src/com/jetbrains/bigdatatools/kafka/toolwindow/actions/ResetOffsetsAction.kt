package com.jetbrains.bigdatatools.kafka.toolwindow.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.MainTreeController.Companion.dataManager
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.MainTreeController.Companion.rfsPath
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver.Companion.isConsumers
import com.jetbrains.bigdatatools.kafka.util.ChangeOffsetController

class ResetOffsetsAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val rfsPath = e.rfsPath ?: return
    val dataManager = e.dataManager as KafkaDataManager

    ChangeOffsetController(e.project ?: return, dataManager, rfsPath.name).showAndUpdate()
  }

  override fun update(e: AnActionEvent) {
    val rfsPath = e.rfsPath
    e.presentation.isEnabledAndVisible = e.dataManager != null && rfsPath?.parent?.isConsumers == true
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}