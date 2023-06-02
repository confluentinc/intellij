package com.jetbrains.bigdatatools.kafka.registry.confluent.controller

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.StatusText
import com.jetbrains.bigdatatools.core.monitoring.data.model.FilterAdapter
import com.jetbrains.bigdatatools.core.monitoring.data.model.FilterKey
import com.jetbrains.bigdatatools.core.monitoring.data.model.ObjectDataModel
import com.jetbrains.bigdatatools.core.monitoring.table.DataTable
import com.jetbrains.bigdatatools.core.monitoring.table.extension.CustomEmptyTextProvider
import com.jetbrains.bigdatatools.core.monitoring.table.model.DataTableModel
import com.jetbrains.bigdatatools.core.monitoring.toolwindow.AbstractTableController
import com.jetbrains.bigdatatools.core.settings.ColumnVisibilitySettings
import com.jetbrains.bigdatatools.core.table.renderers.LinkRenderer
import com.jetbrains.bigdatatools.core.ui.CustomComponentActionImpl
import com.jetbrains.bigdatatools.core.ui.filter.CountFilterPopupComponent
import com.jetbrains.bigdatatools.core.util.invokeLater
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryAddSchemaDialog
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryType
import com.jetbrains.bigdatatools.kafka.registry.common.KafkaSchemaInfo
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.kafka.toolwindow.controllers.KafkaMainController
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import javax.swing.event.DocumentEvent

class KafkaRegistryController(val project: Project,
                              val dataManager: KafkaDataManager,
                              private val mainController: KafkaMainController) : AbstractTableController<KafkaSchemaInfo>() {
  val registryType = dataManager.registryType
  private val model: ObjectDataModel<KafkaSchemaInfo> = dataManager.schemaRegistryModel!!

  private val addSchema = DumbAwareAction.create(KafkaMessagesBundle.message("action.kafka.CreateSchemaAction.text"),
                                                 AllIcons.General.Add) {
    KafkaRegistryAddSchemaDialog(project, dataManager).show()
  }

  private val deleteSchema = object : DumbAwareAction(KafkaMessagesBundle.message("action.Kafka.DeleteSchemaAction.text"), null,
                                                      AllIcons.General.Remove) {
    override fun actionPerformed(e: AnActionEvent) {
      val registryInfo = getSelectedItem() ?: return
      dataManager.deleteSchema(registryInfo.name)
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = getSelectedItem() != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
  }

  private val showSoftDeletedAction = if (dataManager.connectionData.registryType == KafkaRegistryType.CONFLUENT)
    object : DumbAwareToggleAction(KafkaMessagesBundle.message("action.show.deleted.subject.title"), null,
                                   AllIcons.Actions.ToggleVisibility) {
      override fun isSelected(e: AnActionEvent) =
        KafkaToolWindowSettings.getInstance().getOrCreateConfig(dataManager.connectionId).showSoftDeleted

      override fun getActionUpdateThread() = ActionUpdateThread.BGT
      override fun displayTextInToolbar() = false
      override fun setSelected(e: AnActionEvent, state: Boolean) {
        KafkaToolWindowSettings.getInstance().getOrCreateConfig(dataManager.connectionId).showSoftDeleted = state
        dataManager.schemaRegistryModel?.let { dataManager.updater.invokeRefreshModel(it) }
      }
    }
  else {
    null
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

  override fun emptyTextProvider() = CustomEmptyTextProvider { emptyText: StatusText ->
    val clusterConfig = KafkaToolWindowSettings.getInstance().getOrCreateConfig(dataManager.connectionId)
    if (clusterConfig.schemaFilterName.isNullOrBlank()) {
      emptyText.appendText(KafkaMessagesBundle.message("schemas.empty.text"), StatusText.DEFAULT_ATTRIBUTES)
      emptyText.appendLine(KafkaMessagesBundle.message("schemas.empty.text.create.link"),
                           SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) {
        KafkaRegistryAddSchemaDialog(project, dataManager).show()
      }
    }
    else {
      emptyText.appendText(KafkaMessagesBundle.message("schemas.empty.text.filter"), StatusText.DEFAULT_ATTRIBUTES)
      emptyText.appendSecondaryText(KafkaMessagesBundle.message("topics.empty.text.filter.additional"),
                                    SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) {
        clusterConfig.schemaFilterName = null
        dataManager.schemaRegistryModel?.let { dataModel -> dataManager.updater.invokeRefreshModel(dataModel) }
      }
    }
    emptyText.isShowAboveCenter = false
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

  override fun createTopLeftToolbarActions(): List<AnAction> {
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

    return listOfNotNull(CustomComponentActionImpl(searchTextField), CustomComponentActionImpl(countFilter),
                         showSoftDeletedAction)
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


