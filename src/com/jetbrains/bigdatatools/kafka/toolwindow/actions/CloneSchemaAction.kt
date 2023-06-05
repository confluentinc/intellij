package com.jetbrains.bigdatatools.kafka.toolwindow.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.MainTreeController.Companion.dataManager
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.MainTreeController.Companion.rfsPath
import com.jetbrains.bigdatatools.common.util.invokeLater
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryAddSchemaDialog
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver.Companion.isSchemas

class CloneSchemaAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val rfsPath = e.rfsPath ?: return
    val dataManager = e.dataManager as KafkaDataManager
    val project = e.project ?: return


    val schemaInfo = dataManager.getCachedSchema(rfsPath.name) ?: return
    val schemaFormat = schemaInfo.type ?: return
    val version = schemaInfo.version ?: return

    dataManager.getSchemaVersionInfo(schemaInfo.name, version).onSuccess { versionInfo ->
      invokeLater {
        KafkaRegistryAddSchemaDialog(project, dataManager).apply {
          applyRegistryInfo(schemaFormat, versionInfo.schema)
        }.show()
      }
    }
  }

  override fun update(e: AnActionEvent) {
    val rfsPath = e.rfsPath
    e.presentation.isEnabledAndVisible = e.dataManager != null && rfsPath?.parent?.isSchemas == true
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}