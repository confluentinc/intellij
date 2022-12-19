package com.jetbrains.bigdatatools.kafka.toolwindow.controllers


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
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.DetailsMonitoringController
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.TableWithDetailsMonitoringController
import com.jetbrains.bigdatatools.common.settings.ColumnVisibilitySettings
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.model.SchemaRegistryInfo
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryAddSchemaDialog
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

class KafkaSchemaRegistryController(project: Project,
                                    val dataManager: KafkaDataManager) : TableWithDetailsMonitoringController<SchemaRegistryInfo>() {
  private val model: ObjectDataModel<SchemaRegistryInfo> = dataManager.registrySchemaModel!!
  override val detailsController: DetailsMonitoringController = KafkaRegistryTabController(project, dataManager)

  private val showDeleted = object : DumbAwareToggleAction(KafkaMessagesBundle.message("action.show.deleted.subject.title"),
                                                           null, AllIcons.General.Filter) {
    private val settings = KafkaToolWindowSettings.getInstance()

    override fun isSelected(e: AnActionEvent) = settings.registryShowDeletedSubjects
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun setSelected(e: AnActionEvent, state: Boolean) {
      settings.registryShowDeletedSubjects = state
      dataManager.registrySchemaModel?.let { dataManager.autoUpdaterManager.reloadAsync(it) }
    }
  }

  private val addSchema = object : DumbAwareAction(KafkaMessagesBundle.message("action.add.schema.title"), null,
                                                   AllIcons.General.Add) {
    override fun actionPerformed(e: AnActionEvent) {
      val dialog = KafkaRegistryAddSchemaDialog(project, dataManager)
      if (!dialog.showAndGet())
        return
      val schemaName = dialog.getSchemaName()
      val parsedSchema = try {
        dialog.getParsedSchema()
      }
      catch (t: Throwable) {
        return
      }

      dataManager.createRegistrySubject(schemaName, parsedSchema)
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
  }

  private val deleteSchema = object : DumbAwareAction(KafkaMessagesBundle.message("action.remove.schema.title"), null,
                                                      AllIcons.General.Remove) {
    override fun actionPerformed(e: AnActionEvent) {
      val registryInfo = getSelectedItem() ?: return
      if (Messages.showOkCancelDialog(project,
                                      KafkaMessagesBundle.message("action.remove.schema.confirm.dialog.msg", registryInfo.name),
                                      KafkaMessagesBundle.message("action.remove.schema.confirm.dialog.title"),
                                      Messages.getOkButton(),
                                      Messages.getCancelButton(),
                                      Messages.getQuestionIcon()) != Messages.OK) {
        return
      }

      dataManager.deleteRegistrySchema(registryInfo)
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = getSelectedItem()?.id != -1
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
  }

  private val cloneSchema = object : DumbAwareAction(KafkaMessagesBundle.message("action.clone.schema.title"), null,
                                                     AllIcons.Actions.Copy) {
    override fun actionPerformed(e: AnActionEvent) {
      val registryInfo = getSelectedItem() ?: return

      val dialog = KafkaRegistryAddSchemaDialog(project, dataManager).apply {
        applyRegistryInfo(registryInfo)
      }

      if (!dialog.showAndGet())
        return

      val schemaName = dialog.getSchemaName()
      val parsedSchema = try {
        dialog.getParsedSchema()
      }
      catch (t: Throwable) {
        return
      }

      dataManager.createRegistrySubject(schemaName, parsedSchema)
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

  override fun getRenderableColumns() = SchemaRegistryInfo.renderableColumns
  override fun getDataModel() = model
  override fun indexToDetailId(modelIndex: Int) = dataTable.tableModel.getInfoAt(modelIndex)?.id?.toString()
}
