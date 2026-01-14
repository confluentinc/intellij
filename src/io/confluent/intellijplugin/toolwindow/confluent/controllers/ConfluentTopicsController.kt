package io.confluent.intellijplugin.toolwindow.confluent.controllers

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.util.ui.UIUtil
import io.confluent.intellijplugin.ccloud.model.Cluster
import io.confluent.intellijplugin.core.monitoring.data.listener.DataModelListener
import io.confluent.intellijplugin.core.monitoring.data.model.ObjectDataModel
import io.confluent.intellijplugin.core.monitoring.table.DataTable
import io.confluent.intellijplugin.core.monitoring.toolwindow.AbstractTableController
import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import io.confluent.intellijplugin.core.settings.ColumnVisibilitySettings
import io.confluent.intellijplugin.core.table.renderers.FavoriteRenderer
import io.confluent.intellijplugin.core.table.renderers.LinkRenderer
import io.confluent.intellijplugin.core.ui.CustomComponentActionImpl
import io.confluent.intellijplugin.data.ConfluentDataManager
import io.confluent.intellijplugin.model.TopicPresentable
import io.confluent.intellijplugin.model.TopicStatisticInfo
import io.confluent.intellijplugin.rfs.ConfluentDriver
import io.confluent.intellijplugin.toolwindow.config.KafkaToolWindowSettings
import io.confluent.intellijplugin.util.KafkaLocalizedField
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import javax.swing.JLabel
import javax.swing.event.DocumentEvent

/**
 * Controller for displaying topics in Confluent Cloud clusters.
 * Matches the Kafka tool window UI with search, filters, and info panel.
 */
internal class ConfluentTopicsController(
    val project: Project,
    private val dataManager: ConfluentDataManager,
    private val cluster: Cluster,
    private val environmentId: String,
    private val mainController: ConfluentMainController
) : AbstractTableController<TopicPresentable>() {

    // Info panel showing topic statistics (top right)
    val infoPanel = JLabel("").apply {
        foreground = UIUtil.getLabelInfoForeground()
        font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
    }

    // Search text field (top left)
    private val searchTextField = SearchTextField(false).apply {
        addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                // TODO: Implement filtering
                dataManager.updater.invokeRefreshModel(dataManager.getTopicModel(cluster))
            }
        })
    }

    // Toggle to show/hide internal topics
    private val showInternalTopicsAction = object : DumbAwareToggleAction(
        KafkaMessagesBundle.message("show.internal.topic"), null,
        AllIcons.Actions.ToggleVisibility
    ) {
        override fun isSelected(e: AnActionEvent) = KafkaToolWindowSettings.getInstance().showInternalTopics
        override fun getActionUpdateThread() = ActionUpdateThread.BGT
        override fun setSelected(e: AnActionEvent, state: Boolean) {
            KafkaToolWindowSettings.getInstance().showInternalTopics = state
            dataManager.updater.invokeRefreshModel(dataManager.getTopicModel(cluster))
        }
    }

    // Toggle to show only favorite topics
    private val showFavoriteTopicsAction = object : DumbAwareToggleAction(
        KafkaMessagesBundle.message("show.favorite.topic"), null,
        AllIcons.Nodes.Favorite
    ) {
        override fun isSelected(e: AnActionEvent) = KafkaToolWindowSettings.getInstance().showFavoriteTopics
        override fun getActionUpdateThread() = ActionUpdateThread.BGT
        override fun setSelected(e: AnActionEvent, state: Boolean) {
            KafkaToolWindowSettings.getInstance().showFavoriteTopics = state
            dataManager.updater.invokeRefreshModel(dataManager.getTopicModel(cluster))
        }
    }

    // Column visibility settings - show columns with data from Confluent Cloud API
    // Available via API:
    // - name, partitionsCount, replicationFactor, isInternal (basic list API)
    // - messageCount (via internal API /kafka/v3/clusters/{id}/internal/topics/{name}/partitions/-/records:offsets)
    // NOT available via REST API on Basic/Standard clusters:
    // - inSyncReplicas (replicas endpoint returns 404)
    // - replicas, underReplicatedPartitions, noLeaders (not exposed in Confluent Cloud API)
    private val columnSettings = ColumnVisibilitySettings(
        mutableListOf(
            TopicPresentable::isFavorite.name,
            TopicPresentable::name.name,
            TopicPresentable::messageCount.name,
            TopicPresentable::partitions.name,
            TopicPresentable::replicationFactor.name
            // Note: inSyncReplicas currently not available
        )
    )

    init {
        init()

        // Update info panel when data changes
        dataManager.getTopicModel(cluster).addListener(object : DataModelListener {
            override fun onChanged() {
                infoPanel.text = TopicStatisticInfo.createFor(dataManager.getTopics(cluster)).toString()
            }

            override fun onError(msg: String, e: Throwable?) {
                infoPanel.text = TopicStatisticInfo.createFor(dataManager.getTopics(cluster)).toString()
            }
        })
    }

    override fun customTableInit(table: DataTable<TopicPresentable>) {
        // Install favorite renderer on column 0 (star icon)
        FavoriteRenderer.installOnColumn(table, columnModel.getColumn(0)).apply {
            onClick = { row, _ ->
                val topic = table.getDataAt(row)
                // TODO: Implement updatePinedTopics
                // For now, this is a placeholder
                topic?.let {
                    // dataManager.updatePinedTopics(cluster, it.name, !it.isFavorite)
                }
            }
        }

        // Install link renderer on column 1 (topic name - clickable)
        LinkRenderer.installOnColumn(table, columnModel.getColumn(1)).apply {
            onClick = { row, _ ->
                val topicName = table.getDataAt(row)?.name
                topicName?.let {
                    // Build the RFS path for the topic: env/Clusters/clusterId/Topics/topicName
                    val topicPath = RfsPath(
                        listOf(
                            environmentId,
                            ConfluentDriver.CLUSTERS_FOLDER,
                            cluster.id,
                            ConfluentDriver.TOPICS_FOLDER,
                            it
                        ),
                        false
                    )
                    mainController.open(topicPath)
                }
            }
        }
    }

    override fun createTopLeftToolbarActions(): List<AnAction> {
        // TODO: Add CountFilterPopupComponent for topic limit
        return listOf(
            CustomComponentActionImpl(searchTextField),
            showFavoriteTopicsAction,
            showInternalTopicsAction
        )
    }

    override fun createTopRightToolbarActions(): List<AnAction> {
        return listOf(CustomComponentActionImpl(infoPanel))
    }

    override fun getColumnSettings(): ColumnVisibilitySettings = columnSettings

    override fun getRenderableColumns(): List<KafkaLocalizedField<TopicPresentable>> =
        TopicPresentable.renderableColumns

    override fun getDataModel(): ObjectDataModel<TopicPresentable>? =
        dataManager.getTopicModel(cluster)

    override fun showColumnFilter(): Boolean = false

    override fun getAdditionalActions(): List<AnAction> = listOf()

    override fun getAdditionalContextActions(): List<AnAction> = listOf()
}
