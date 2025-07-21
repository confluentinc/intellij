package com.jetbrains.bigdatatools.kafka.toolwindow.actions

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightVirtualFile
import com.jetbrains.bigdatatools.kafka.common.editor.KafkaEditorProvider
import com.jetbrains.bigdatatools.kafka.common.models.KafkaEditorType
import com.jetbrains.bigdatatools.kafka.core.monitoring.toolwindow.MainTreeController.Companion.dataManager
import com.jetbrains.bigdatatools.kafka.core.monitoring.toolwindow.MainTreeController.Companion.rfsPath
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.manager.DriverManager
import com.jetbrains.bigdatatools.kafka.core.settings.actions.CreateConnectionPopup
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver.Companion.isTopicFolder
import com.jetbrains.bigdatatools.kafka.toolwindow.controllers.KafkaFileType
import javax.swing.JComponent

class KafkaCreateProducerAction : DumbAwareAction(), CustomComponentAction {

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return ActionButtonWithText(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val dataManager = e.dataManager as? KafkaDataManager
    if (dataManager != null) {
      val rfsPath = e.rfsPath
      val defaultTopic = if (rfsPath?.parent?.isTopicFolder == true)
        rfsPath.name
      else
        null

      openProducer(dataManager, project, defaultTopic)
    }
    else {
      val actions = DriverManager.getDrivers(project).filterIsInstance<KafkaDriver>().map { driver ->
        create(driver.connectionData.name) {
          openProducer(driver.dataManager, project, null)
        }
      }

      val additional = listOf(Separator(), ActionManager.getInstance().getAction("Kafka.GlobalCreateKafkaConnection"))
      CreateConnectionPopup.createPopup(DefaultActionGroup(actions + additional), e).showCenteredInCurrentWindow(project)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  companion object {
    fun openProducer(dataManager: KafkaDataManager, project: Project, defaultTopic: String?): Array<FileEditor> {
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