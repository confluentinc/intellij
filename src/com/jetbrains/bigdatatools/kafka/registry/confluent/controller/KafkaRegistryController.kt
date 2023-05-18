package com.jetbrains.bigdatatools.kafka.registry.confluent.controller

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.jetbrains.bigdatatools.common.monitoring.data.model.FilterAdapter
import com.jetbrains.bigdatatools.common.monitoring.data.model.FilterKey
import com.jetbrains.bigdatatools.common.monitoring.data.model.ObjectDataModel
import com.jetbrains.bigdatatools.common.monitoring.table.DataTable
import com.jetbrains.bigdatatools.common.monitoring.table.model.DataTableModel
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.AbstractTableController
import com.jetbrains.bigdatatools.common.settings.ColumnVisibilitySettings
import com.jetbrains.bigdatatools.common.table.renderers.LinkRenderer
import com.jetbrains.bigdatatools.common.ui.CustomComponentActionImpl
import com.jetbrains.bigdatatools.common.ui.filter.CountFilterPopupComponent
import com.jetbrains.bigdatatools.common.util.ToolbarUtils
import com.jetbrains.bigdatatools.common.util.invokeLater
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryAddSchemaDialog
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryType
import com.jetbrains.bigdatatools.kafka.registry.common.KafkaSchemaInfo
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.kafka.toolwindow.controllers.KafkaMainController
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import javax.swing.event.DocumentEvent

class KafkaRegistryController(project: Project,
                              val dataManager: KafkaDataManager,
                              private val mainController: KafkaMainController) : AbstractTableController<KafkaSchemaInfo>() {
  val registryType = dataManager.registryType
  private val model: ObjectDataModel<KafkaSchemaInfo> = dataManager.schemaRegistryModel!!

  private val addSchema = object : DumbAwareAction(KafkaMessagesBundle.message("action.kafka.CreateSchemaAction.text"), null,
                                                   AllIcons.General.Add) {
    override fun actionPerformed(e: AnActionEvent) {
      KafkaRegistryAddSchemaDialog(project, dataManager).show()
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
  }

  private val deleteSchema = object : DumbAwareAction(KafkaMessagesBundle.message("action.Kafka.DeleteSchemaAction.text"), null,
                                                      AllIcons.General.Remove) {
    override fun actionPerformed(e: AnActionEvent) {
      val registryInfo = getSelectedItem() ?: return

      if (Messages.showOkCancelDialog(project,
                                      KafkaMessagesBundle.message("action.remove.schema.confirm.dialog.msg.soft", registryInfo.name),
                                      KafkaMessagesBundle.message("action.remove.schema.confirm.dialog.title"),
                                      Messages.getOkButton(),
                                      Messages.getCancelButton(),
                                      Messages.getQuestionIcon()) == Messages.OK) {
        dataManager.deleteSchema(registryInfo)
      }
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = getSelectedItem() != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
  }

  private val cloneSchema = object : DumbAwareAction(KafkaMessagesBundle.message("action.kafka.CloneSchemaAction.text"), null,
                                                     AllIcons.Actions.Copy) {
    override fun actionPerformed(e: AnActionEvent) {
      val schemaInfo = getSelectedItem() ?: return
      val schemaFormat = schemaInfo.type ?: return
      val version = schemaInfo.version ?: return

      dataManager.getSchemaVersionInfo(schemaInfo.name, version).onSuccess { versionInfo ->
        invokeLater {
          KafkaRegistryAddSchemaDialog(project, dataManager).apply {
            applyRegistryInfo(schemaFormat, versionInfo.schema)
          }.show()
        }
      }
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = getSelectedItem()?.isSoftDeleted == false
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
  }

  init {
    init()
  }

  override fun customTableInit(table: DataTable<KafkaSchemaInfo>) {
    LinkRenderer.installOnColumn(table, columnModel.getColumn(0)).apply {
      onClick = { row, _ ->
        @Suppress("UNCHECKED_CAST")
        val schema = (table.model as? DataTableModel<KafkaSchemaInfo>)?.getInfoAt(row)?.name
        schema?.let {
          mainController.open(KafkaDriver.schemasPath.child(it, false))
        }
      }
    }
  }


  override fun createTopToolBar(): ActionToolbar {
    val searchTextField = SearchTextField(false).apply {
      addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
          val config = KafkaToolWindowSettings.getInstance().getOrCreateConfig(dataManager.connectionId)
          config.schemaFilterName = this@apply.text
          dataManager.schemaRegistryModel?.let { dataManager.updater.invokeRefreshModel(it) }
        }
      })
    }


    val countFilter = CountFilterPopupComponent(KafkaMessagesBundle.message("label.filter.limit"),
                                                KafkaToolWindowSettings.getInstance().getOrCreateConfig(
                                                  dataManager.connectionId).registryLimit)
    FilterAdapter.install(dataTable.tableModel, countFilter, LIMIT_FILTER) { limit ->
      val config = KafkaToolWindowSettings.getInstance().getOrCreateConfig(dataManager.connectionId)
      config.registryLimit = limit
      dataManager.schemaRegistryModel?.let { dataManager.updater.invokeRefreshModel(it) }
    }


    val toolbar = DefaultActionGroup(CustomComponentActionImpl(searchTextField), CustomComponentActionImpl(countFilter), Separator(),
                                     addSchema, deleteSchema, cloneSchema)
    return ToolbarUtils.createActionToolbar("BDTKafkaTopicsTopToolbar", toolbar, true)
  }


  override fun getAdditionalContextActions(): List<AnAction> = listOf(addSchema, deleteSchema, cloneSchema)

  override fun getColumnSettings(): ColumnVisibilitySettings = when (registryType) {
    KafkaRegistryType.NONE -> error("Should not be invoked")
    KafkaRegistryType.CONFLUENT -> KafkaToolWindowSettings.getInstance().confluentSchemaTableColumnSettings
    KafkaRegistryType.AWS_GLUE -> KafkaToolWindowSettings.getInstance().glueSchemaTableColumnSettings
  }

  override fun showColumnFilter(): Boolean = false

  override fun getRenderableColumns() = KafkaSchemaInfo.renderableColumns
  override fun getDataModel() = model

  companion object {
    val LIMIT_FILTER = FilterKey("registryLimit")
  }
}

