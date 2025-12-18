package io.confluent.intellijplugin.registry.confluent.controller

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.StatusText
import io.confluent.intellijplugin.core.monitoring.data.model.FilterAdapter
import io.confluent.intellijplugin.core.monitoring.data.model.FilterKey
import io.confluent.intellijplugin.core.monitoring.data.model.ObjectDataModel
import io.confluent.intellijplugin.core.monitoring.table.DataTable
import io.confluent.intellijplugin.core.monitoring.table.extension.CustomEmptyTextProvider
import io.confluent.intellijplugin.core.monitoring.toolwindow.AbstractTableController
import io.confluent.intellijplugin.core.monitoring.toolwindow.MainTreeController
import io.confluent.intellijplugin.core.settings.ColumnVisibilitySettings
import io.confluent.intellijplugin.core.table.renderers.FavoriteRenderer
import io.confluent.intellijplugin.core.table.renderers.LinkRenderer
import io.confluent.intellijplugin.core.ui.CustomComponentActionImpl
import io.confluent.intellijplugin.core.ui.filter.CountFilterPopupComponent
import io.confluent.intellijplugin.data.KafkaDataManager
import io.confluent.intellijplugin.registry.KafkaRegistryAddSchemaDialog
import io.confluent.intellijplugin.registry.KafkaRegistryType
import io.confluent.intellijplugin.registry.common.KafkaSchemaInfo
import io.confluent.intellijplugin.rfs.KafkaDriver
import io.confluent.intellijplugin.toolwindow.config.KafkaToolWindowSettings
import io.confluent.intellijplugin.toolwindow.kafka.controllers.KafkaMainController
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent

internal class KafkaRegistryController(
    val project: Project,
    val dataManager: KafkaDataManager,
    private val mainController: KafkaMainController
) : AbstractTableController<KafkaSchemaInfo>() {
    val registryType = dataManager.registryType
    private val model: ObjectDataModel<KafkaSchemaInfo> = dataManager.schemaRegistryModel!!

    private val searchTextField: SearchTextField = SearchTextField(false).apply {
        addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                val config = KafkaToolWindowSettings.getInstance().getOrCreateConfig(dataManager.connectionId)
                config.schemaFilterName = this@apply.text
                dataManager.schemaRegistryModel?.let { dataManager.updater.invokeRefreshModel(it) }
            }
        })

        text = KafkaToolWindowSettings.getInstance().getOrCreateConfig(dataManager.connectionId).schemaFilterName ?: ""
    }

    private val showFavoriteSchemasAction = object : DumbAwareToggleAction(
        KafkaMessagesBundle.message("action.show.favorite.schemas"), null,
        AllIcons.Nodes.Favorite
    ) {
        override fun isSelected(e: AnActionEvent) = KafkaToolWindowSettings.getInstance().showFavoriteSchema
        override fun getActionUpdateThread() = ActionUpdateThread.BGT
        override fun setSelected(e: AnActionEvent, state: Boolean) {
            KafkaToolWindowSettings.getInstance().showFavoriteSchema = state
            dataManager.schemaRegistryModel?.let { dataManager.updater.invokeRefreshModel(it) }
        }
    }

    init {
        init()

        dataTable.selectionModel.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION

        dataTable.customDataProvider = UiDataProvider { sink ->
            sink[MainTreeController.DATA_MANAGER] = dataManager
            sink[MainTreeController.RFS_PATH] =
                getSelectedItem()?.name?.let { KafkaDriver.schemasPath.child(it, false) }
        }
    }

    override fun emptyTextProvider() = CustomEmptyTextProvider { emptyText: StatusText ->
        val toolWindowSettings = KafkaToolWindowSettings.getInstance()
        val clusterConfig = toolWindowSettings.getOrCreateConfig(dataManager.connectionId)
        if (!clusterConfig.schemaFilterName.isNullOrBlank() || toolWindowSettings.showFavoriteSchema) {
            emptyText.appendText(
                KafkaMessagesBundle.message("schemas.empty.text.filter"),
                StatusText.DEFAULT_ATTRIBUTES
            )
            emptyText.appendSecondaryText(
                KafkaMessagesBundle.message("topics.empty.text.filter.additional"),
                SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES
            ) {
                clusterConfig.schemaFilterName = null
                toolWindowSettings.showFavoriteSchema = false
                searchTextField.text = ""
                dataManager.schemaRegistryModel?.let { dataModel -> dataManager.updater.invokeRefreshModel(dataModel) }
            }
        } else {
            emptyText.appendText(KafkaMessagesBundle.message("schemas.empty.text"), StatusText.DEFAULT_ATTRIBUTES)
            emptyText.appendLine(
                KafkaMessagesBundle.message("schemas.empty.text.create.link"),
                SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES
            ) {
                KafkaRegistryAddSchemaDialog(project, dataManager).show()
            }
        }
        emptyText.isShowAboveCenter = false
    }

    override fun customTableInit(table: DataTable<KafkaSchemaInfo>) {
        FavoriteRenderer.installOnColumn(table, columnModel.getColumn(0)).apply {
            onClick = { row, _ ->
                val schemaInfo = table.getDataAt(row)
                schemaInfo?.let { dataManager.updatePinedSchemas(it.name, !it.isFavorite) }
            }
        }

        LinkRenderer.installOnColumn(table, columnModel.getColumn(1)).apply {
            onClick = { row, _ ->
                val schema = table.getDataAt(row)?.name
                schema?.let {
                    mainController.open(KafkaDriver.schemasPath.child(it, false))
                }
            }
        }
    }

    override fun createTopLeftToolbarActions(): List<AnAction> {

        val countFilter = CountFilterPopupComponent(
            KafkaMessagesBundle.message("label.filter.limit"),
            KafkaToolWindowSettings.getInstance().getOrCreateConfig(
                dataManager.connectionId
            ).registryLimit
        )
        FilterAdapter.install(dataTable.tableModel, countFilter, LIMIT_FILTER) { limit ->
            val config = KafkaToolWindowSettings.getInstance().getOrCreateConfig(dataManager.connectionId)
            config.registryLimit = limit
            dataManager.schemaRegistryModel?.let { dataManager.updater.invokeRefreshModel(it) }
        }

        return listOfNotNull(
            CustomComponentActionImpl(searchTextField),
            CustomComponentActionImpl(countFilter),
            showFavoriteSchemasAction
        )
    }

    override fun getAdditionalContextActions(): List<AnAction> {
        val actionManager = ActionManager.getInstance()
        val group = actionManager.getAction("Kafka.Schema.Actions") as DefaultActionGroup
        return group.getChildren(actionManager).toList()
    }

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


