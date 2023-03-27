package com.jetbrains.bigdatatools.kafka.registry.confluent.controller

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.DetailsTableMonitoringController
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.model.SchemaRegistryFieldsInfo
import com.jetbrains.bigdatatools.kafka.registry.ui.KafkaSchemaInfoDialog
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

class ConfluentSchemaFieldsController(private val project: Project,
                                      private val dataManager: KafkaDataManager) : DetailsTableMonitoringController<SchemaRegistryFieldsInfo, String>() {
  private val showSchema = object : DumbAwareAction(KafkaMessagesBundle.message("show.schema.info"), null,
                                                    AllIcons.Actions.ToggleVisibility) {
    override fun actionPerformed(e: AnActionEvent) {
      val registryInfo = selectedId?.let { dataManager.confluentSchemaRegistry?.getSchemaInfo(it.toInt()) } ?: return

      KafkaSchemaInfoDialog.show(project = project, schemaType = registryInfo.type, schemaDefinition = registryInfo.schema,
                                 schemaName = registryInfo.name)
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
  }

  private val editSchema = object : DumbAwareAction(KafkaMessagesBundle.message("edit.schema.info"), null,
                                                    AllIcons.Actions.EditScheme) {
    override fun actionPerformed(e: AnActionEvent) {
      val registryInfo = selectedId?.let { dataManager.confluentSchemaRegistry?.getSchemaInfo(it.toInt()) } ?: return

      KafkaSchemaInfoDialog.showDiff(KafkaMessagesBundle.message("update.dialog.title"), project, registryInfo) { newText ->
        dataManager.confluentSchemaRegistry?.updateSchema(registryInfo, newText)!!
      }
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
  }

  init {
    init()
  }

  override fun getAdditionalActions(): List<AnAction> = listOf(showSchema, editSchema)
  override fun showColumnFilter(): Boolean = false
  override fun getColumnSettings() = KafkaToolWindowSettings.getInstance().schemaRegistryFieldsTableColumnSettings
  override fun getRenderableColumns() = SchemaRegistryFieldsInfo.renderableColumns
  override fun getDataModel() = selectedId?.let { dataManager.confluentSchemaRegistry?.getRegistrySchemaFieldsModel(it.toInt()) }
}