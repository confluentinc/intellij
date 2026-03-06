package io.confluent.intellijplugin.toolwindow.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import io.confluent.intellijplugin.core.monitoring.toolwindow.MainTreeController.Companion.dataManager
import io.confluent.intellijplugin.core.monitoring.toolwindow.MainTreeController.Companion.rfsPath
import io.confluent.intellijplugin.data.KafkaDataManager
import io.confluent.intellijplugin.data.CCloudClusterDataManager
import io.confluent.intellijplugin.rfs.KafkaDriver.Companion.isSchemas
import io.confluent.intellijplugin.rfs.ConfluentDriver.Companion.isSchemas as isConfluentSchemas

class DeleteSchemaAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val rfsPath = e.rfsPath ?: return
        val dataManager = e.dataManager ?: return

        when (dataManager) {
            is KafkaDataManager -> dataManager.deleteSchema(rfsPath.name)
            is CCloudClusterDataManager -> dataManager.deleteSchema(rfsPath.name)
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