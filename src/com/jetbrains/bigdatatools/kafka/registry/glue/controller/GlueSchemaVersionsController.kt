package com.jetbrains.bigdatatools.kafka.registry.glue.controller

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.jetbrains.bigdatatools.common.monitoring.table.extension.TableColumnsFitter
import com.jetbrains.bigdatatools.common.monitoring.table.extension.TableLoadingDecorator
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.DetailsMonitoringController
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.TableWithDetailsMonitoringController
import com.jetbrains.bigdatatools.common.util.executeOnPooledThread
import com.jetbrains.bigdatatools.common.util.invokeLater
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.registry.glue.models.GlueSchemaVersionInfo
import com.jetbrains.bigdatatools.kafka.registry.glue.models.SchemaVersionId
import com.jetbrains.bigdatatools.kafka.registry.ui.KafkaSchemaInfoDialog
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import software.amazon.awssdk.services.glue.model.SchemaId
import javax.swing.ListSelectionModel

class GlueSchemaVersionsController(private val project: Project,
                                   private val dataManager: KafkaDataManager) : TableWithDetailsMonitoringController<GlueSchemaVersionInfo, SchemaVersionId>(),
                                                                                DetailsMonitoringController<SchemaId> {
  private var selectedId: SchemaId? = null

  private val deleteSchema = object : DumbAwareAction(KafkaMessagesBundle.message("action.remove.version.title"), null,
                                                      AllIcons.General.Remove) {
    override fun actionPerformed(e: AnActionEvent) {
      val registryInfo = getSelectedItem() ?: return

      val res = Messages.showOkCancelDialog(project,
                                            KafkaMessagesBundle.message("action.remove.version.confirm.dialog.msg", registryInfo.version,
                                                                        registryInfo.schemaId.schemaName()),
                                            KafkaMessagesBundle.message("action.remove.version.title"),
                                            KafkaMessagesBundle.message("action.remove.schema.version.confirm.ok"),
                                            Messages.getCancelButton(),
                                            Messages.getQuestionIcon())
      if (res == Messages.OK)
        dataManager.glueSchemaRegistry?.deleteSchemaVersion(registryInfo)

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
      val schemaVersionInfo = getSelectedItem() ?: return
      val id = selectedId ?: return

      val schemaInfo = dataManager.glueSchemaRegistry?.getDetailedSchema(id) ?: return
      val schemaName = schemaInfo.schemaResponse.schemaName()
      executeOnPooledThread {
        val schemaVersion = dataManager.glueSchemaRegistry.client.getSchemaVersion(schemaName, version = schemaVersionInfo.version)
        invokeLater {
          KafkaSchemaInfoDialog.show(project = project,
                                     schemaType = schemaInfo.schemaResponse.dataFormatAsString(),
                                     schemaDefinition = schemaVersion.schemaDefinition(),
                                     schemaName = schemaName)

        }
      }
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

      val id = selectedId ?: return
      val schemaInfo = dataManager.glueSchemaRegistry?.getDetailedSchema(id) ?: return

      val schemaName = schemaInfo.schemaResponse.schemaName()
      val registryName = schemaInfo.schemaResponse.registryName()
      val schemaType = schemaInfo.schemaResponse.dataFormatAsString()

      val versionInfo1 = dataTable.tableModel.getInfoAt(dataTable.convertRowIndexToModel(dataTable.selectedRows[0])) ?: return
      val versionInfo2 = dataTable.tableModel.getInfoAt(dataTable.convertRowIndexToModel(dataTable.selectedRows[1])) ?: return


      executeOnPooledThread {
        val versionDetailed1 = dataManager.glueSchemaRegistry.loadSchemaVersion(schemaName,
                                                                                versionInfo1.version)
        val versionDetailed2 = dataManager.glueSchemaRegistry.loadSchemaVersion(schemaName,
                                                                                versionInfo2.version)

        invokeLater {
          KafkaSchemaInfoDialog.showDiff(project,
                                         KafkaMessagesBundle.message("update.dialog.title"),
                                         schemaName = schemaName,
                                         schemaType = schemaType,
                                         schemaDefinition1 = versionDetailed1.schemaDefinition(),
                                         schemaDefinition2 = versionDetailed2.schemaDefinition()) { newText ->
            dataManager.glueSchemaRegistry.registerNewSchemaVersion(registryName, schemaName, newText)
          }
        }
      }

    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = getSelectedItem() != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
  }

  override val detailsController = GlueSchemaFieldsController(dataManager)

  init {
    init()
    dataTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
  }

  override fun indexToDetailId(modelIndex: Int): SchemaVersionId? {
    return dataTable.tableModel.getInfoAt(modelIndex)?.schemaVersionId
  }

  override fun saveSelectedItem() {}

  override fun getAdditionalActions(): List<AnAction> = listOf(deleteSchema, viewSchema, showDiff)

  override fun showColumnFilter(): Boolean = false

  override fun getColumnSettings() = KafkaToolWindowSettings.getInstance().glueSchemaVersionsTableColumnsSettings

  override fun getRenderableColumns() = GlueSchemaVersionInfo.renderableColumns

  override fun getDataModel() = selectedId?.let { dataManager.glueSchemaRegistry?.getRegistrySchemaVersionsModel(it) }

  override fun setDetailsId(id: SchemaId) {
    selectedId = id

    val model = getDataModel() ?: return
    dataTable.tableModel.setDataModel(model)

    TableColumnsFitter.get(dataTable)?.reset()
    TableLoadingDecorator.installOn(dataTable)

    decoratedTableComponent.revalidate()
    decoratedTableComponent.repaint()
  }

}