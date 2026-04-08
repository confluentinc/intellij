package io.confluent.intellijplugin.toolwindow.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import io.confluent.intellijplugin.core.monitoring.toolwindow.MainTreeController.Companion.dataManager
import io.confluent.intellijplugin.core.monitoring.toolwindow.MainTreeController.Companion.rfsPath
import io.confluent.intellijplugin.core.util.invokeLater
import io.confluent.intellijplugin.data.KafkaDataManager
import io.confluent.intellijplugin.data.CCloudClusterDataManager
import io.confluent.intellijplugin.registry.KafkaRegistryAddSchemaDialog
import io.confluent.intellijplugin.rfs.KafkaDriver.Companion.isSchemas
import io.confluent.intellijplugin.rfs.ConfluentDriver.Companion.isSchemas as isConfluentSchemas

class CloneSchemaAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val rfsPath = e.rfsPath ?: return
        val dataManager = e.dataManager ?: return
        val project = e.project ?: return

        val schemaInfo = when (dataManager) {
            is KafkaDataManager -> dataManager.getCachedSchema(rfsPath.name)
            is CCloudClusterDataManager -> dataManager.getCachedSchema(rfsPath.name)
            else -> null
        } ?: return

        val schemaFormat = schemaInfo.type ?: return
        val version = schemaInfo.version ?: return

        when (dataManager) {
            is KafkaDataManager -> {
                dataManager.getSchemaVersionInfo(schemaInfo.name, version).onSuccess { versionInfo ->
                    invokeLater {
                        KafkaRegistryAddSchemaDialog(project, dataManager).apply {
                            applyRegistryInfo(schemaFormat, versionInfo.schema)
                        }.show()
                    }
                }
            }
            is CCloudClusterDataManager -> {
                dataManager.getSchemaVersionInfo(schemaInfo.name, version).onSuccess { versionInfo ->
                    invokeLater {
                        KafkaRegistryAddSchemaDialog(project, dataManager).apply {
                            applyRegistryInfo(schemaFormat, versionInfo.schema)
                        }.show()
                    }
                }
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val rfsPath = e.rfsPath
        val dataManager = e.dataManager

        // Check if it's a Kafka or CCloud schema registry path
        val isSchemaPath = rfsPath?.parent?.isSchemas == true ||
                          rfsPath?.parent?.isConfluentSchemas == true

        e.presentation.isEnabledAndVisible = dataManager != null &&
            (dataManager is KafkaDataManager || dataManager is CCloudClusterDataManager) &&
            isSchemaPath
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}