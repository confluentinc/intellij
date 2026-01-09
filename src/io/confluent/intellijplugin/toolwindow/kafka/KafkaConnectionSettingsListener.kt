package io.confluent.intellijplugin.toolwindow.kafka

import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import io.confluent.intellijplugin.common.editor.KafkaEditorProvider
import io.confluent.intellijplugin.core.settings.ConnectionSettingsListener
import io.confluent.intellijplugin.core.settings.ModificationKey
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import io.confluent.intellijplugin.rfs.KafkaConnectionData

/** If Kafka connection settings is modified, we are closing all editor tabs (Consumer/Producer). */
class KafkaConnectionSettingsListener : ConnectionSettingsListener {
    override fun onConnectionModified(
        project: Project?,
        connectionData: ConnectionData,
        modified: Collection<ModificationKey>
    ) {
        if (connectionData !is KafkaConnectionData) {
            return
        }

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