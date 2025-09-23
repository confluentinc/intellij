package io.confluent.kafka.toolwindow.actions

import com.intellij.ide.actions.NewElementAction
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import io.confluent.kafka.core.monitoring.toolwindow.MainTreeController.Companion.dataManager
import io.confluent.kafka.core.monitoring.toolwindow.MainTreeController.Companion.rfsPath
import io.confluent.kafka.data.KafkaDataManager
import io.confluent.kafka.registry.KafkaRegistryAddSchemaDialog
import io.confluent.kafka.rfs.KafkaDriver.Companion.isSchemas

class CreateSchemaAction : NewElementAction(), ActionPromoter, DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val dataManager = e.dataManager as KafkaDataManager
    val project = e.project ?: return
    KafkaRegistryAddSchemaDialog(project, dataManager).show()
  }

  override fun update(e: AnActionEvent) {
    val rfsPath = e.rfsPath
    e.presentation.isEnabledAndVisible = e.dataManager != null && rfsPath?.parent?.isSchemas == true || rfsPath?.isSchemas == true
  }

  override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction> {
    return listOf(this)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}