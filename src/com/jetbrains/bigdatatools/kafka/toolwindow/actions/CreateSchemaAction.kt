package com.jetbrains.bigdatatools.kafka.toolwindow.actions

import com.intellij.ide.actions.NewElementAction
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryAddSchemaDialog
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver.Companion.isSchemas
import com.jetbrains.bigdatatools.kafka.toolwindow.controllers.KafkaMainController.Companion.dataManager
import com.jetbrains.bigdatatools.kafka.toolwindow.controllers.KafkaMainController.Companion.rfsPath

class CreateSchemaAction : NewElementAction(), ActionPromoter, DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val dataManager = e.dataManager
    val project = e.project ?: return
    KafkaRegistryAddSchemaDialog(project, dataManager).show()
  }

  override fun update(e: AnActionEvent) {
    val rfsPath = e.rfsPath
    e.presentation.isEnabledAndVisible = rfsPath?.parent?.isSchemas == true || rfsPath?.isSchemas == true
  }

  override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction> {
    return listOf(this)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}