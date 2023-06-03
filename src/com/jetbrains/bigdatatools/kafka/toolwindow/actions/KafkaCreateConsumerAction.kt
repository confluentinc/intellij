package com.jetbrains.bigdatatools.kafka.toolwindow.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.testFramework.LightVirtualFile
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.MainTreeController.Companion.dataManager
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.MainTreeController.Companion.rfsPath
import com.jetbrains.bigdatatools.kafka.common.editor.KafkaEditorProvider
import com.jetbrains.bigdatatools.kafka.common.models.KafkaEditorType
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver.Companion.isTopicFolder
import com.jetbrains.bigdatatools.kafka.statistics.KafkaUsagesCollector
import com.jetbrains.bigdatatools.kafka.toolwindow.controllers.KafkaFileType

class KafkaCreateConsumerAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val dataManager = e.dataManager as KafkaDataManager
    val project = e.project ?: return
    val rfsPath = e.rfsPath
    val defaultTopic = if (rfsPath?.parent?.isTopicFolder == true)
      rfsPath.name
    else
      null
    FileEditorManager.getInstance(project).openFile(createConsumerFile(dataManager, defaultTopic), true)
    KafkaUsagesCollector.openConsumerEvent.log(project)
  }


  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private fun createConsumerFile(dataManager: KafkaDataManager, defaultTopic: String?): LightVirtualFile {
    val connectionData = dataManager.connectionData

    return LightVirtualFile("${connectionData.name} Consumer", KafkaFileType(), "").apply {
      putUserData(KafkaEditorProvider.KAFKA_MANAGER_KEY, dataManager)
      putUserData(KafkaEditorProvider.KAFKA_EDITOR_TYPE, KafkaEditorType.CONSUMER)
      putUserData(KafkaEditorProvider.KAFKA_DEFAULT_TOPIC, defaultTopic)
    }
  }

}