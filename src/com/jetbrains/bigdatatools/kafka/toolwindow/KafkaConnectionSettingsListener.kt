package com.jetbrains.bigdatatools.kafka.toolwindow

import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.jetbrains.bigdatatools.kafka.common.editor.KafkaEditorProvider
import com.jetbrains.bigdatatools.common.settings.ConnectionSettingsListener
import com.jetbrains.bigdatatools.common.settings.ModificationKey
import com.jetbrains.bigdatatools.common.settings.connections.ConnectionData

class KafkaConnectionSettingsListener : ConnectionSettingsListener {
  override fun onConnectionModified(project: Project?, connectionData: ConnectionData, modified: Collection<ModificationKey>) {

    val projects = if (project == null) ProjectManager.getInstance().openProjects else arrayOf(project)
    projects.forEach { prj ->
      val editorManager = FileEditorManagerEx.getInstanceEx(prj)
      editorManager.openFiles.toList().forEach { file ->
        val dataManager = file.getUserData(KafkaEditorProvider.KAFKA_MANAGER_KEY) ?: return
        if (dataManager.connectionId != connectionData.innerId) return
        val window = editorManager.windows.find { it.isFileOpen(file) } ?: return
        editorManager.closeFile(file, window)
      }
    }
  }
}