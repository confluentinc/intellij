package com.jetbrains.bigdatatools.kafka.registry.glue.controller

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.jetbrains.bigdatatools.common.monitoring.data.model.FieldsGroupModel
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.AbstractGroupFieldsModelsController
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.AbstractTableController
import com.jetbrains.bigdatatools.common.util.ToolbarUtils
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.registry.glue.models.GlueSchemaDetailedInfo
import com.jetbrains.bigdatatools.kafka.registry.ui.KafkaSchemaInfoDialog
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import software.amazon.awssdk.services.glue.model.SchemaId
import java.awt.BorderLayout

class GlueSchemaInfoController(project: Project, override val dataManager: KafkaDataManager) :
  AbstractGroupFieldsModelsController<SchemaId>(project, dataManager.connectionData.innerId) {

  override val toolWindowSettings = KafkaToolWindowSettings.getInstance()

  private val showSchema = object : DumbAwareAction(KafkaMessagesBundle.message("show.schema.info"), null,
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

  private val editSchema = object : DumbAwareAction(KafkaMessagesBundle.message("edit.schema.info"), null,
                                                    AllIcons.Actions.EditScheme) {
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

  init {
    init()

    ToolbarUtils.createToolbar(listOf(showSchema, editSchema), place = AbstractTableController.TABLE_TOOLBAR_PLACE)?.also {
      it.targetComponent = component
      val toolbarComponent = it.component.apply {
        border = IdeBorderFactory.createBorder(SideBorder.RIGHT)
      }
      component.add(toolbarComponent, BorderLayout.WEST)
    }
  }

  override fun getFieldsGroupModel(id: SchemaId): FieldsGroupModel<GlueSchemaDetailedInfo> =
    dataManager.glueSchemaRegistry!!.getRegistrySchemaInfoModel(id)
}