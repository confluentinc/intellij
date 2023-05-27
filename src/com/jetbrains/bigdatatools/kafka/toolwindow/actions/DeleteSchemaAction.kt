package com.jetbrains.bigdatatools.kafka.toolwindow.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver.Companion.isSchemas
import com.jetbrains.bigdatatools.kafka.toolwindow.controllers.KafkaMainController.Companion.dataManager
import com.jetbrains.bigdatatools.kafka.toolwindow.controllers.KafkaMainController.Companion.rfsPath

class DeleteSchemaAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val rfsPath = e.rfsPath ?: return
    e.dataManager.deleteSchema(rfsPath.name)
  }

  override fun update(e: AnActionEvent) {
    val rfsPath = e.rfsPath
    e.presentation.isEnabledAndVisible = rfsPath?.parent?.isSchemas == true
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}