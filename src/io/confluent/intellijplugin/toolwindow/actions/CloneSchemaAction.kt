package io.confluent.intellijplugin.toolwindow.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import io.confluent.intellijplugin.core.monitoring.toolwindow.MainTreeController.Companion.dataManager
import io.confluent.intellijplugin.core.monitoring.toolwindow.MainTreeController.Companion.rfsPath
import io.confluent.intellijplugin.core.util.invokeLater
import io.confluent.intellijplugin.data.KafkaDataManager
import io.confluent.intellijplugin.registry.KafkaRegistryAddSchemaDialog
import io.confluent.intellijplugin.rfs.KafkaDriver.Companion.isSchemas

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