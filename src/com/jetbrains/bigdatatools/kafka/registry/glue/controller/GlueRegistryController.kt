package com.jetbrains.bigdatatools.kafka.registry.glue.controller

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.jetbrains.bigdatatools.common.monitoring.data.model.ObjectDataModel
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.TableWithDetailsMonitoringController
import com.jetbrains.bigdatatools.common.settings.ColumnVisibilitySettings
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryAddSchemaDialog
import com.jetbrains.bigdatatools.kafka.registry.glue.models.GlueSchemaInfo
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import software.amazon.awssdk.services.glue.model.SchemaId

class GlueRegistryController(project: Project,
                             val dataManager: KafkaDataManager) : TableWithDetailsMonitoringController<GlueSchemaInfo, SchemaId>() {
  private val model: ObjectDataModel<GlueSchemaInfo> = dataManager.glueSchemaRegistry?.schemaModel!!
  override val detailsController = GlueTabController(project, dataManager)

  private val showDeleted = object : DumbAwareToggleAction(KafkaMessagesBundle.message("action.show.deleted.subject.title"),
                                                           null, AllIcons.General.Filter) {
    private val settings = KafkaToolWindowSettings.getInstance()

    override fun isSelected(e: AnActionEvent) = settings.registryShowDeletedSubjects
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun setSelected(e: AnActionEvent, state: Boolean) {
      settings.registryShowDeletedSubjects = state
      dataManager.glueSchemaRegistry?.schemaModel?.let { dataManager.updater.invokeRefreshModel(it) }
    }
  }

  private val addSchema = object : DumbAwareAction(KafkaMessagesBundle.message("action.add.schema.title"), null,
                                                   AllIcons.General.Add) {
    override fun actionPerformed(e: AnActionEvent) {
      KafkaRegistryAddSchemaDialog(project, dataManager).show()

    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
  }

  private val deleteSchema = object : DumbAwareAction(KafkaMessagesBundle.message("action.remove.schema.title"), null,
                                                      AllIcons.General.Remove) {
    override fun actionPerformed(e: AnActionEvent) {
      val schemaInfo = getSelectedItem() ?: return

      if (Messages.showOkCancelDialog(project,
                                      KafkaMessagesBundle.message("action.remove.schema.confirm.dialog.msg.soft", schemaInfo.schemaName),
                                      KafkaMessagesBundle.message("action.remove.schema.confirm.dialog.title"),
                                      Messages.getOkButton(),
                                      Messages.getCancelButton(),
                                      Messages.getQuestionIcon()) == Messages.OK) {
        dataManager.glueSchemaRegistry?.deleteSchema(schemaName = schemaInfo.schemaName)
      }
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = getSelectedItem() != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
  }

  private val cloneSchema = object : DumbAwareAction(KafkaMessagesBundle.message("action.clone.schema.title"), null,
                                                     AllIcons.Actions.Copy) {
    override fun actionPerformed(e: AnActionEvent) {
      val schemaInfo = getSelectedItem() ?: return

      KafkaRegistryAddSchemaDialog(project, dataManager).apply {
        val detailedInfo = dataManager.glueSchemaRegistry?.getDetailedSchema(
          SchemaId.builder().registryName(schemaInfo.registryName).schemaName(schemaInfo.schemaName).build())
        val schemaDefinition = detailedInfo?.versionResponse?.schemaDefinition() ?: ""
        applyRegistryInfo(detailedInfo?.schemaResponse?.dataFormatAsString() ?: "", schemaDefinition)
      }.show()
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = getSelectedItem() != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
  }

  init {
    detailsSplitter.proportion = 0.3f
    init()
  }

  override fun getAdditionalActions(): List<AnAction> = listOf(showDeleted, addSchema, deleteSchema, cloneSchema)

  override fun saveSelectedItem() {}
  override fun getColumnSettings(): ColumnVisibilitySettings = KafkaToolWindowSettings.getInstance().glueSchemaTableColumnSettings
  override fun showColumnFilter(): Boolean = false

  override fun getRenderableColumns() = GlueSchemaInfo.renderableColumns
  override fun getDataModel() = model
  override fun indexToDetailId(modelIndex: Int) = dataTable.tableModel.getInfoAt(modelIndex)?.id
}
