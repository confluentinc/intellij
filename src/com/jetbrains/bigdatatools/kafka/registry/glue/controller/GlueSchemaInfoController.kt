package com.jetbrains.bigdatatools.kafka.registry.glue.controller

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.AbstractGroupFieldsModelsController
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.registry.ui.KafkaSchemaInfoDialog
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import software.amazon.awssdk.services.glue.model.SchemaId

class GlueSchemaInfoController(project: Project, override val dataManager: KafkaDataManager) :
  AbstractGroupFieldsModelsController<SchemaId>(project, dataManager.connectionData.innerId) {

  override val toolWindowSettings = KafkaToolWindowSettings.getInstance()

  init {
    init()
  }

  override fun getFieldsGroupModel(id: SchemaId) = dataManager.glueSchemaRegistry!!.getRegistrySchemaInfoModel(id)

  override fun createActions(): List<AnAction> {
    val showSchema = object : DumbAwareAction(KafkaMessagesBundle.message("show.schema.info"), null,
                                              AllIcons.Actions.ToggleVisibility) {
      override fun actionPerformed(e: AnActionEvent) {
        val schemaId = this@GlueSchemaInfoController.id ?: return
        val schemaInfo = schemaId.let { dataManager.glueSchemaRegistry?.getDetailedSchema(it) } ?: return
        KafkaSchemaInfoDialog.show(project = project,
                                   schemaType = schemaInfo.schemaResponse.dataFormatAsString(),
                                   schemaDefinition = schemaInfo.versionResponse.schemaDefinition(),
                                   schemaName = schemaInfo.schemaResponse.schemaName())
      }

      override fun getActionUpdateThread() = ActionUpdateThread.BGT
    }

    val editSchema = object : DumbAwareAction(KafkaMessagesBundle.message("edit.schema.info"), null, AllIcons.Actions.EditScheme) {
      override fun actionPerformed(e: AnActionEvent) {
        val schemaId = this@GlueSchemaInfoController.id ?: return
        val info = schemaId.let { dataManager.glueSchemaRegistry?.getDetailedSchema(it) } ?: return

        KafkaSchemaInfoDialog.showDiff(project,
                                       KafkaMessagesBundle.message("update.dialog.title"),
                                       schemaName = info.schemaResponse.schemaName(),
                                       schemaType = info.schemaResponse.dataFormatAsString(),
                                       schemaDefinition1 = info.versionResponse.schemaDefinition(),
                                       schemaDefinition2 = info.versionResponse.schemaDefinition()) { newText ->
          dataManager.glueSchemaRegistry?.registerNewSchemaVersion(info.schemaResponse.registryName(), info.schemaResponse.schemaName(),
                                                                   newText)!!
        }
      }

      override fun getActionUpdateThread() = ActionUpdateThread.BGT
    }

    return super.createActions() + listOf(showSchema, editSchema)
  }
}