package com.jetbrains.bigdatatools.kafka.toolwindow.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightVirtualFile
import com.jetbrains.bigdatatools.kafka.common.editor.KafkaEditorProvider
import com.jetbrains.bigdatatools.kafka.common.models.KafkaEditorType
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.statistics.KafkaUsagesCollector
import com.jetbrains.bigdatatools.kafka.toolwindow.controllers.KafkaFileType
import com.jetbrains.bigdatatools.kafka.toolwindow.controllers.KafkaMainController.Companion.dataManager

class KafkaCreateProducerAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val dataManager = e.dataManager
    val project = e.project ?: return
    openProducer(dataManager, project)
    KafkaUsagesCollector.openProducerEvent.log(project)
  }

  override fun displayTextInToolbar() = true

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private fun openProducer(dataManager: KafkaDataManager, project: Project): Array<FileEditor> {
    val connectionData = dataManager.connectionData
    val file = LightVirtualFile("${connectionData.name} Producer", KafkaFileType(), "").apply {
      putUserData(KafkaEditorProvider.KAFKA_MANAGER_KEY, dataManager)
      putUserData(KafkaEditorProvider.KAFKA_EDITOR_TYPE, KafkaEditorType.PRODUCER)
    }
    return FileEditorManager.getInstance(project).openFile(file, true)
  }
}