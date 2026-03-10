package io.confluent.intellijplugin.toolwindow.actions

import com.intellij.ide.actions.NewElementAction
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import io.confluent.intellijplugin.core.monitoring.toolwindow.MainTreeController.Companion.dataManager
import io.confluent.intellijplugin.core.monitoring.toolwindow.MainTreeController.Companion.rfsPath
import io.confluent.intellijplugin.data.KafkaDataManager
import io.confluent.intellijplugin.data.CCloudClusterDataManager
import io.confluent.intellijplugin.registry.KafkaRegistryAddSchemaDialog
import io.confluent.intellijplugin.rfs.KafkaDriver.Companion.isSchemas
import io.confluent.intellijplugin.rfs.ConfluentDriver.Companion.isSchemas as isConfluentSchemas

class CreateSchemaAction : NewElementAction(), ActionPromoter, DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val dataManager = e.dataManager ?: return
        val project = e.project ?: return

        when (dataManager) {
            is KafkaDataManager -> KafkaRegistryAddSchemaDialog(project, dataManager).show()
            is CCloudClusterDataManager -> KafkaRegistryAddSchemaDialog(project, dataManager).show()
        }
    }

    override fun update(e: AnActionEvent) {
        val rfsPath = e.rfsPath
        val dataManager = e.dataManager

        // Check if it's a Kafka or CCloud schema registry path
        val isSchemaPath = rfsPath?.parent?.isSchemas == true ||
                          rfsPath?.isSchemas == true ||
                          rfsPath?.parent?.isConfluentSchemas == true ||
                          rfsPath?.isConfluentSchemas == true

        e.presentation.isEnabledAndVisible = dataManager != null &&
            (dataManager is KafkaDataManager || dataManager is CCloudClusterDataManager) &&
            isSchemaPath
    }

    override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction> {
        return listOf(this)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}