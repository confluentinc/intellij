package com.jetbrains.bigdatatools.kafka.toolwindow.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightVirtualFile
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.MainTreeController.Companion.dataManager
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.MainTreeController.Companion.rfsPath
import com.jetbrains.bigdatatools.kafka.common.editor.KafkaEditorProvider
import com.jetbrains.bigdatatools.kafka.common.models.KafkaEditorType
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver.Companion.isTopicFolder
import com.jetbrains.bigdatatools.kafka.statistics.KafkaUsagesCollector
import com.jetbrains.bigdatatools.kafka.toolwindow.controllers.KafkaFileType

class KafkaCreateProducerAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val dataManager = e.dataManager as KafkaDataManager
    val project = e.project ?: return
    val rfsPath = e.rfsPath
    val defaultTopic = if (rfsPath?.parent?.isTopicFolder == true)
      rfsPath.name
    else
      null

    openProducer(dataManager, project, defaultTopic)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.dataManager != null
  }

  companion object {
    fun openProducer(dataManager: KafkaDataManager, project: Project, defaultTopic: String?): Array<FileEditor> {
      KafkaUsagesCollector.openProducerEvent.log(dataManager.project)
      val connectionData = dataManager.connectionData
      val file = LightVirtualFile("${connectionData.name} Producer", KafkaFileType(), "").apply {
        putUserData(KafkaEditorProvider.KAFKA_MANAGER_KEY, dataManager)
        putUserData(KafkaEditorProvider.KAFKA_EDITOR_TYPE, KafkaEditorType.PRODUCER)
        putUserData(KafkaEditorProvider.KAFKA_DEFAULT_TOPIC, defaultTopic)
      }
      return FileEditorManager.getInstance(project).openFile(file, true)
    }
  }
}