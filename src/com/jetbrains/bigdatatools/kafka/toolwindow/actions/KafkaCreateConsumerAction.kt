package com.jetbrains.bigdatatools.kafka.toolwindow.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.testFramework.LightVirtualFile
import com.jetbrains.bigdatatools.kafka.common.editor.KafkaEditorProvider
import com.jetbrains.bigdatatools.kafka.common.models.KafkaEditorType
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.statistics.KafkaUsagesCollector
import com.jetbrains.bigdatatools.kafka.toolwindow.controllers.KafkaFileType
import com.jetbrains.bigdatatools.kafka.toolwindow.controllers.KafkaMainController.Companion.dataManager

class KafkaCreateConsumerAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val dataManager = e.dataManager
    val project = e.project ?: return
    FileEditorManager.getInstance(project).openFile(createConsumerFile(dataManager), true)
    KafkaUsagesCollector.openConsumerEvent.log(project)
  }

  override fun displayTextInToolbar() = true

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private fun createConsumerFile(dataManager: KafkaDataManager): LightVirtualFile {
    val connectionData = dataManager.connectionData

    return LightVirtualFile("${connectionData.name} Consumer", KafkaFileType(), "").apply {
      putUserData(KafkaEditorProvider.KAFKA_MANAGER_KEY, dataManager)
      putUserData(KafkaEditorProvider.KAFKA_EDITOR_TYPE, KafkaEditorType.CONSUMER)
    }
  }

}