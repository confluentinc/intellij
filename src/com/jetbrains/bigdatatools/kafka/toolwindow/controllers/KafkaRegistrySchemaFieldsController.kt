package com.jetbrains.bigdatatools.kafka.toolwindow.controllers

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.icons.AllIcons
import com.intellij.json.JsonFileType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.DetailsTableMonitoringController
import com.jetbrains.bigdatatools.common.ui.BdtJsonInfoDialog
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.model.SchemaRegistryFieldsInfo
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryUtil
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

class KafkaRegistrySchemaFieldsController(val project: Project,
                                          private val dataManager: KafkaDataManager) : DetailsTableMonitoringController<SchemaRegistryFieldsInfo>() {
  private val showSchema = object : DumbAwareAction(KafkaMessagesBundle.message("show.schema.info"), null,
                                                    AllIcons.General.Information) {
    override fun actionPerformed(e: AnActionEvent) {
      val registryInfo = selectedId?.let { dataManager.getSchemaInfo(it.toInt()) } ?: return
      val schema = KafkaRegistryUtil.getPrettySchema(registryInfo) ?: return

      BdtJsonInfoDialog(project, registryInfo.name, schema).show()
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
  }

  private val editSchema = object : DumbAwareAction(KafkaMessagesBundle.message("edit.schema.info"), null,
                                                    AllIcons.Actions.EditScheme) {
    override fun actionPerformed(e: AnActionEvent) {
      val registryInfo = selectedId?.let { dataManager.getSchemaInfo(it.toInt()) } ?: return
      val schema = KafkaRegistryUtil.getPrettySchema(registryInfo) ?: return

      val prev = DiffContentFactory.getInstance().create(schema, JsonFileType.INSTANCE).also {
        it.document.setReadOnly(true)
      }
      val new = DiffContentFactory.getInstance().create(schema, JsonFileType.INSTANCE)
      new.document.setReadOnly(false)
      val diffData = SimpleDiffRequest(KafkaMessagesBundle.message("show.edit.schema.diff.title", registryInfo.name),
                                       prev,
                                       new,
                                       KafkaMessagesBundle.message("show.edit.schema.diff.prev.name"),
                                       KafkaMessagesBundle.message("show.edit.schema.diff.new.name"))
      DiffManager.getInstance().showDiff(project, diffData, DiffDialogHints.MODAL)
      val newText = new.document.text
      if (prev.document.text != newText) {
        dataManager.updateSchema(registryInfo, newText)
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
  override fun getDataModel() = selectedId?.let { dataManager.getRegistrySchemaFieldsModel(it.toInt()) }
}