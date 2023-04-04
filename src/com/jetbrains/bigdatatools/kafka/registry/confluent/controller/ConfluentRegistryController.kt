package com.jetbrains.bigdatatools.kafka.registry.confluent.controller

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBPanelWithEmptyText
import com.jetbrains.bigdatatools.common.monitoring.data.model.ObjectDataModel
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.TableWithDetailsMonitoringController
import com.jetbrains.bigdatatools.common.settings.ColumnVisibilitySettings
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryAddSchemaDialog
import com.jetbrains.bigdatatools.kafka.registry.confluent.ConfluentSchemaInfo
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

class ConfluentRegistryController(project: Project,
                                  val dataManager: KafkaDataManager) : TableWithDetailsMonitoringController<ConfluentSchemaInfo, String>() {
  private val model: ObjectDataModel<ConfluentSchemaInfo> = dataManager.confluentSchemaRegistry?.schemaRegistryModel!!
  override val detailsController = ConfluentSchemaVersionsController(project, dataManager)

  private val showDeleted = object : DumbAwareToggleAction(KafkaMessagesBundle.message("action.show.deleted.subject.title"),
                                                           null, AllIcons.General.Filter) {
    private val settings = KafkaToolWindowSettings.getInstance()

    override fun isSelected(e: AnActionEvent) = settings.registryShowDeletedSubjects
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun setSelected(e: AnActionEvent, state: Boolean) {
      settings.registryShowDeletedSubjects = state
      dataManager.confluentSchemaRegistry?.schemaRegistryModel?.let { dataManager.updater.invokeRefreshModel(it) }
    }
  }

  private val addSchema = object : DumbAwareAction(KafkaMessagesBundle.message("action.add.schema.title"), null,
                                                   AllIcons.General.Add) {
    override fun actionPerformed(e: AnActionEvent) {
      KafkaRegistryAddSchemaDialog(project, dataManager).show()
      //if (!dialog.showAndGet())
      //  return
      //val schemaName = dialog.getSchemaName()
      //val parsedSchema = try {
      //  dialog.getParsedSchema()
      //}
      //catch (t: Throwable) {
      //  return
      //}
      //
      //dataManager.createRegistrySubject(schemaName, parsedSchema)
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
  }

  private val deleteSchema = object : DumbAwareAction(KafkaMessagesBundle.message("action.remove.schema.title"), null,
                                                      AllIcons.General.Remove) {
    override fun actionPerformed(e: AnActionEvent) {
      val registryInfo = getSelectedItem() ?: return
      if (registryInfo.id == -1) { // Special case for soft deleted items.
        if (Messages.showOkCancelDialog(project,
                                        KafkaMessagesBundle.message("action.remove.schema.confirm.dialog.msg.permanent", registryInfo.name),
                                        KafkaMessagesBundle.message("action.remove.schema.confirm.dialog.title"),
                                        Messages.getOkButton(),
                                        Messages.getCancelButton(),
                                        Messages.getQuestionIcon()) == Messages.OK) {
          dataManager.confluentSchemaRegistry?.deleteRegistrySchema(registryInfo, true)
        }
        return
      }

      if (Messages.showOkCancelDialog(project,
                                      KafkaMessagesBundle.message("action.remove.schema.confirm.dialog.msg.soft", registryInfo.name),
                                      KafkaMessagesBundle.message("action.remove.schema.confirm.dialog.title"),
                                      Messages.getOkButton(),
                                      Messages.getCancelButton(),
                                      Messages.getQuestionIcon()) == Messages.OK) {
        dataManager.confluentSchemaRegistry?.deleteRegistrySchema(registryInfo, false)
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
      val registryInfo = getSelectedItem() ?: return

      KafkaRegistryAddSchemaDialog(project, dataManager).apply {
        applyRegistryInfo(registryInfo.type, registryInfo.schema)
      }.show()
      //
      //if (!dialog.showAndGet())
      //  return
      //
      //val schemaName = dialog.getSchemaName()
      //val parsedSchema = try {
      //  dialog.getParsedSchema()
      //}
      //catch (t: Throwable) {
      //  return
      //}
      //
      //dataManager.createRegistrySubject(schemaName, parsedSchema)
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = getSelectedItem()?.id != -1
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
  }

  init {
    detailsSplitter.proportion = 0.3f
    init()
  }

  override fun showDetails() {
    if (getSelectedItem()?.id == -1) {
      detailsSplitter.secondComponent = JBPanelWithEmptyText().withEmptyText(
        KafkaMessagesBundle.message("schema.registry.deleted")
      )
      return
    }
    super.showDetails()
  }

  override fun getAdditionalActions(): List<AnAction> = listOf(
    showDeleted, addSchema, deleteSchema, cloneSchema
  )

  override fun saveSelectedItem() {}
  override fun getColumnSettings(): ColumnVisibilitySettings = KafkaToolWindowSettings.getInstance().schemaRegistryTableColumnSettings
  override fun showColumnFilter(): Boolean = false

  override fun getRenderableColumns() = ConfluentSchemaInfo.renderableColumns
  override fun getDataModel() = model
  override fun indexToDetailId(modelIndex: Int) = dataTable.tableModel.getInfoAt(modelIndex)?.id?.toString()
}
