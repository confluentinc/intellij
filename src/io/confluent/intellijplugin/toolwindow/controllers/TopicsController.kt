package io.confluent.intellijplugin.toolwindow.controllers

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.UIUtil
import io.confluent.intellijplugin.core.monitoring.data.MonitoringDataManager
import io.confluent.intellijplugin.core.monitoring.data.listener.DataModelListener
import io.confluent.intellijplugin.core.monitoring.data.model.FilterAdapter
import io.confluent.intellijplugin.core.monitoring.data.model.FilterKey
import io.confluent.intellijplugin.core.monitoring.table.DataTable
import io.confluent.intellijplugin.core.monitoring.table.extension.CustomEmptyTextProvider
import io.confluent.intellijplugin.core.monitoring.toolwindow.AbstractTableController
import io.confluent.intellijplugin.core.monitoring.toolwindow.MainTreeController
import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import io.confluent.intellijplugin.core.table.renderers.FavoriteRenderer
import io.confluent.intellijplugin.core.table.renderers.LinkRenderer
import io.confluent.intellijplugin.core.ui.CustomComponentActionImpl
import io.confluent.intellijplugin.core.ui.filter.CountFilterPopupComponent
import io.confluent.intellijplugin.data.BaseClusterDataManager
import io.confluent.intellijplugin.data.CCloudClusterDataManager
import io.confluent.intellijplugin.model.TopicPresentable
import io.confluent.intellijplugin.model.TopicStatisticInfo
import io.confluent.intellijplugin.rfs.KafkaDriver
import io.confluent.intellijplugin.toolwindow.NavigableController
import io.confluent.intellijplugin.toolwindow.actions.KafkaCreateConsumerAction
import io.confluent.intellijplugin.toolwindow.actions.KafkaCreateProducerAction
import io.confluent.intellijplugin.toolwindow.config.KafkaToolWindowSettings
import io.confluent.intellijplugin.util.KafkaDialogFactory
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JViewport
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent

internal class TopicsController(
    val project: Project,
    private val dataManager: BaseClusterDataManager,
    private val mainController: NavigableController
) : AbstractTableController<TopicPresentable>() {
    val infoPanel = JLabel("").apply {
        foreground = UIUtil.getLabelInfoForeground()
        font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
    }

    private val searchTextField = SearchTextField(false).apply {
        addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                val config = KafkaToolWindowSettings.getInstance().getOrCreateConfig(dataManager.connectionId)
                config.topicFilterName = this@apply.text
                dataManager.updater.invokeRefreshModel(dataManager.topicModel)
            }
        })

        text = KafkaToolWindowSettings.getInstance().getOrCreateConfig(dataManager.connectionId).topicFilterName ?: ""
    }


    private val showInternalTopicsAction = object : DumbAwareToggleAction(
        KafkaMessagesBundle.message("show.internal.topic"), null,
        AllIcons.Actions.ToggleVisibility
    ) {
        override fun isSelected(e: AnActionEvent) = KafkaToolWindowSettings.getInstance().showInternalTopics
        override fun getActionUpdateThread() = ActionUpdateThread.BGT
        override fun setSelected(e: AnActionEvent, state: Boolean) {
            KafkaToolWindowSettings.getInstance().showInternalTopics = state
            dataManager.updater.invokeRefreshModel(dataManager.topicModel)
        }
    }

    private val showFavoriteTopicsAction = object : DumbAwareToggleAction(
        KafkaMessagesBundle.message("show.favorite.topic"), null,
        AllIcons.Nodes.Favorite
    ) {
        override fun isSelected(e: AnActionEvent) = KafkaToolWindowSettings.getInstance().showFavoriteTopics
        override fun getActionUpdateThread() = ActionUpdateThread.BGT
        override fun setSelected(e: AnActionEvent, state: Boolean) {
            KafkaToolWindowSettings.getInstance().showFavoriteTopics = state
            dataManager.updater.invokeRefreshModel(dataManager.topicModel)
        }
    }

    init {
        init()

        dataTable.selectionModel.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION

        dataTable.customDataProvider = UiDataProvider { sink ->
            sink[MainTreeController.DATA_MANAGER] = dataManager as MonitoringDataManager

            // Generate the correct RFS path based on selection and data manager type
            val selectedTopicName = getSelectedItem()?.name
            sink[MainTreeController.RFS_PATH] = when (dataManager) {
                is CCloudClusterDataManager -> {
                    if (selectedTopicName != null) {
                        // Confluent Cloud topic: clusterId/topicName
                        RfsPath(listOf(dataManager.connectionId, selectedTopicName), false)
                    } else {
                        // No selection: provide cluster path (clusterId)
                        RfsPath(listOf(dataManager.connectionId), true)
                    }
                }
                else -> {
                    if (selectedTopicName != null) {
                        // Kafka topic: Topics/topicName
                        KafkaDriver.topicPath.child(selectedTopicName, false)
                    } else {
                        // No selection: provide Topics folder path
                        KafkaDriver.topicPath
                    }
                }
            }
        }

        dataManager.topicModel.addListener(object : DataModelListener {
            override fun onChanged() {
                updateUIForEmptyState()
                infoPanel.text = TopicStatisticInfo.createFor(dataManager.getTopics()).toString()
            }

            override fun onError(msg: String, e: Throwable?) {
                updateUIForEmptyState()
                infoPanel.text = TopicStatisticInfo.createFor(dataManager.getTopics()).toString()
            }
        })

        updateUIForEmptyState()
    }

    private fun updateUIForEmptyState() {
        val toolWindowSettings = KafkaToolWindowSettings.getInstance()
        val clusterConfig = toolWindowSettings.getOrCreateConfig(dataManager.connectionId)
        val hasFilters = !clusterConfig.topicFilterName.isNullOrBlank() ||
                toolWindowSettings.showFavoriteTopics ||
                toolWindowSettings.showInternalTopics
        val hasTopics = dataManager.getTopics().isNotEmpty()

        val shouldShowToolbar = hasTopics || hasFilters

        val layout = decoratedTableComponent.layout as? BorderLayout
        val northComponent = layout?.getLayoutComponent(BorderLayout.NORTH)
        northComponent?.isVisible = shouldShowToolbar

        dataTable.tableHeader?.isVisible = shouldShowToolbar

        infoPanel.isVisible = hasTopics

        if (!hasTopics) {
            updateEmptyText()
        }
    }

    private fun updateEmptyText() {
        val toolWindowSettings = KafkaToolWindowSettings.getInstance()
        val clusterConfig = toolWindowSettings.getOrCreateConfig(dataManager.connectionId)

        if (dataTable.parent is JViewport) {
            dataTable.emptyText.attachTo(dataTable, dataTable)
        }

        dataTable.emptyText.clear()

        if (!clusterConfig.topicFilterName.isNullOrBlank() || toolWindowSettings.showFavoriteTopics || toolWindowSettings.showInternalTopics) {
            dataTable.emptyText.appendText(KafkaMessagesBundle.message("topics.empty.text.filter"), StatusText.DEFAULT_ATTRIBUTES)
            dataTable.emptyText.appendSecondaryText(
                KafkaMessagesBundle.message("topics.empty.text.filter.additional"),
                SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES
            ) {
                clusterConfig.topicFilterName = null
                toolWindowSettings.showFavoriteTopics = false
                toolWindowSettings.showInternalTopics = false
                searchTextField.text = ""
                dataManager.updater.invokeRefreshModel(dataManager.topicModel)
            }
        } else {
            dataTable.emptyText.appendText(KafkaMessagesBundle.message("topics.empty.text"))
            dataTable.emptyText.appendLine(
                KafkaMessagesBundle.message("topics.text.create.link"),
                SimpleTextAttributes.LINK_ATTRIBUTES
            ) {
                KafkaDialogFactory.showCreateTopicDialog(dataManager)
            }
        }

        dataTable.emptyText.isShowAboveCenter = true
    }

    override fun customTableInit(table: DataTable<TopicPresentable>) {
        FavoriteRenderer.installOnColumn(table, columnModel.getColumn(0)).apply {
            onClick = { row, _ ->
                val topic = table.getDataAt(row)
                topic?.let { dataManager.updatePinnedTopics(it.name, !it.isFavorite) }
            }
        }

        LinkRenderer.installOnColumn(table, columnModel.getColumn(1)).apply {
            onClick = { row, _ ->
                val topicName = table.getDataAt(row)?.name
                topicName?.let {
                    val path = when (dataManager) {
                        is CCloudClusterDataManager -> {
                            // Confluent Cloud: clusterId/topicName
                            RfsPath(listOf(dataManager.connectionId, it), false)
                        }
                        else -> {
                            // Kafka: Topics/topicName
                            KafkaDriver.topicPath.child(it, false)
                        }
                    }
                    mainController.open(path)
                }
            }
        }
    }

    override fun createTopLeftToolbarActions(): List<AnAction> {

        val countFilter = CountFilterPopupComponent(
            KafkaMessagesBundle.message("label.filter.limit"),
            KafkaToolWindowSettings.getInstance().getOrCreateConfig(
                dataManager.connectionId
            ).topicLimit
        )
        FilterAdapter.install(dataTable.tableModel, countFilter, LIMIT_FILTER) { limit ->
            val config = KafkaToolWindowSettings.getInstance().getOrCreateConfig(dataManager.connectionId)
            config.topicLimit = limit
            dataManager.updater.invokeRefreshModel(dataManager.topicModel)
        }

        return listOf(
            CustomComponentActionImpl(searchTextField),
            CustomComponentActionImpl(countFilter),
            showFavoriteTopicsAction,
            showInternalTopicsAction
        )
    }

    override fun emptyTextProvider() = CustomEmptyTextProvider { emptyText: StatusText ->
        updateEmptyText()
    }

    override fun getColumnSettings() = if (dataManager.supportsInSyncReplicasData()) {
        KafkaToolWindowSettings.getInstance().kafkaTopicColumnSettings
    } else {
        KafkaToolWindowSettings.getInstance().ccloudTopicColumnSettings
    }

    override fun getRenderableColumns() = TopicPresentable.renderableColumns
    override fun getDataModel() = dataManager.topicModel

    private fun createConsumeAction(): AnAction {
        return object : DumbAwareAction(
            KafkaMessagesBundle.message("action.kafka.create.consumer.text"),
            KafkaMessagesBundle.message("action.kafka.create.consumer.description"),
            AllIcons.Actions.Execute
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                val topic = getSelectedItem()?.name
                KafkaCreateConsumerAction.createConsumer(project, dataManager, topic)
            }

            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
        }
    }

    private fun createProduceAction(): AnAction {
        return object : DumbAwareAction(
            KafkaMessagesBundle.message("action.kafka.create.producer.text"),
            KafkaMessagesBundle.message("action.kafka.create.producer.description"),
            AllIcons.Actions.Upload
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                val topic = getSelectedItem()?.name
                KafkaCreateProducerAction.openProducer(dataManager, project, topic)
            }

            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
        }
    }

    override fun showColumnFilter(): Boolean = false

    override fun getAdditionalContextActions(): List<AnAction> {
        val actions = mutableListOf<AnAction>()

        actions.add(createConsumeAction())
        actions.add(createProduceAction())

        val actionManager = ActionManager.getInstance()
        val group = actionManager.getAction("Kafka.Topic.Actions") as DefaultActionGroup
        actions.addAll(group.getChildren(actionManager).toList())

        return actions
    }

    override fun createTopRightToolbarActions() = listOf(CustomComponentActionImpl(infoPanel))

    companion object {
        val LIMIT_FILTER = FilterKey("topicLimit")
    }
}