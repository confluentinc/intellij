package com.jetbrains.bigdatatools.kafka.toolwindow.controllers

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.DetailsTableMonitoringController
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.model.SchemaRegistryInfo
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

class KafkaRegistrySchemaVersionsController(val project: Project,
                                            private val dataManager: KafkaDataManager) : DetailsTableMonitoringController<SchemaRegistryInfo>() {
  @Suppress("DuplicatedCode")
  private val deleteSchema = object : DumbAwareAction(KafkaMessagesBundle.message("action.remove.version.title"),
                                                      null,
                                                      AllIcons.General.Remove) {
    override fun actionPerformed(e: AnActionEvent) {
      val registryInfo = getSelectedItem() ?: return
      if (Messages.showOkCancelDialog(project,
                                      KafkaMessagesBundle.message("action.remove.version.confirm.dialog.msg", registryInfo.version,
                                                                  registryInfo.name),
                                      KafkaMessagesBundle.message("action.remove.schema.confirm.dialog.title"),
                                      Messages.getOkButton(),
                                      Messages.getCancelButton(),
                                      Messages.getQuestionIcon()) != Messages.OK) {
        return
      }
      val isPermanent = false //TODO: @nikita.pavlenko please add do dialog check
      dataManager.deleteRegistrySchemaVersion(registryInfo, isPermanent)
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = getSelectedItem() != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
  }


  init {
    init()
  }

  override fun getAdditionalActions(): List<AnAction> = listOf(deleteSchema)

  override fun showColumnFilter(): Boolean = false

  override fun getColumnSettings() = KafkaToolWindowSettings.getInstance().schemaRegistryVersionsTableColumnsSettings

  override fun getRenderableColumns() = SchemaRegistryInfo.renderableColumns

  override fun getDataModel() = selectedId?.let { dataManager.getRegistrySchemaVersionsModel(it.toInt()) }
}