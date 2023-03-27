package com.jetbrains.bigdatatools.kafka.registry.confluent.controller

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.util.PairFunction
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.DetailsTableMonitoringController
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.registry.confluent.ConfluentSchemaInfo
import com.jetbrains.bigdatatools.kafka.registry.ui.KafkaSchemaInfoDialog
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import javax.swing.JCheckBox
import javax.swing.ListSelectionModel

class ConfluentSchemaVersionsController(private val project: Project,
                                        private val dataManager: KafkaDataManager) : DetailsTableMonitoringController<ConfluentSchemaInfo, String>() {

  private val deleteSchema = object : DumbAwareAction(KafkaMessagesBundle.message("action.remove.version.title"), null,
                                                      AllIcons.General.Remove) {
    override fun actionPerformed(e: AnActionEvent) {
      val registryInfo = getSelectedItem() ?: return

      Messages.showCheckboxMessageDialog(KafkaMessagesBundle.message("action.remove.version.confirm.dialog.msg", registryInfo.version,
                                                                     registryInfo.name),
                                         KafkaMessagesBundle.message("action.remove.schema.confirm.dialog.title"),
                                         arrayOf(Messages.getOkButton(), Messages.getCancelButton()),
                                         KafkaMessagesBundle.message("action.remove.version.confirm.dialog.option"),
                                         false, 0, 0,
                                         Messages.getQuestionIcon(),
                                         PairFunction { exitCode: Int, cb: JCheckBox ->
                                           if (exitCode == Messages.OK) {
                                             dataManager.confluentSchemaRegistry?.deleteRegistrySchemaVersion(registryInfo, cb.isSelected)
                                           }
                                           exitCode
                                         })
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = getSelectedItem() != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
  }

  private val viewSchema = object : DumbAwareAction(KafkaMessagesBundle.message("show.schema.info"),
                                                    null,
                                                    AllIcons.Actions.ToggleVisibility) {
    override fun actionPerformed(e: AnActionEvent) {
      val registryInfo = getSelectedItem() ?: return
      KafkaSchemaInfoDialog.show(project = project, schemaType = registryInfo.type, schemaDefinition = registryInfo.schema,
                                 schemaName = registryInfo.name)
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = getSelectedItem() != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
  }

  private val showDiff = object : DumbAwareAction(KafkaMessagesBundle.message("action.diff.version.title"),
                                                  null,
                                                  AllIcons.Actions.Diff) {
    override fun actionPerformed(e: AnActionEvent) {

      if (dataTable.selectedRows.size != 2) {
        Messages.showInfoMessage(project, KafkaMessagesBundle.message("action.diff.select.two.message"), "")
        return
      }

      val firstSchema = dataTable.tableModel.getInfoAt(dataTable.convertRowIndexToModel(dataTable.selectedRows[0])) ?: return
      val secondSchema = dataTable.tableModel.getInfoAt(dataTable.convertRowIndexToModel(dataTable.selectedRows[1])) ?: return

      KafkaSchemaInfoDialog.showDiff(KafkaMessagesBundle.message("diff.dialog.title"), project, firstSchema, secondSchema)
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = getSelectedItem() != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
  }

  init {
    init()
    dataTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
  }

  override fun getAdditionalActions(): List<AnAction> = listOf(deleteSchema, viewSchema, showDiff)

  override fun showColumnFilter(): Boolean = false

  override fun getColumnSettings() = KafkaToolWindowSettings.getInstance().schemaRegistryVersionsTableColumnsSettings

  override fun getRenderableColumns() = ConfluentSchemaInfo.renderableColumns

  override fun getDataModel() = selectedId?.let { dataManager.confluentSchemaRegistry?.getRegistrySchemaVersionsModel(it.toInt()) }
}