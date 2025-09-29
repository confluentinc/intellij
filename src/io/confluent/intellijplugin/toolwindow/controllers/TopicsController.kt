package io.confluent.intellijplugin.toolwindow.controllers

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.UIUtil
import io.confluent.intellijplugin.core.monitoring.data.listener.DataModelListener
import io.confluent.intellijplugin.core.monitoring.data.model.FilterAdapter
import io.confluent.intellijplugin.core.monitoring.data.model.FilterKey
import io.confluent.intellijplugin.core.monitoring.table.DataTable
import io.confluent.intellijplugin.core.monitoring.table.extension.CustomEmptyTextProvider
import io.confluent.intellijplugin.core.monitoring.toolwindow.AbstractTableController
import io.confluent.intellijplugin.core.monitoring.toolwindow.MainTreeController
import io.confluent.intellijplugin.core.table.renderers.FavoriteRenderer
import io.confluent.intellijplugin.core.table.renderers.LinkRenderer
import io.confluent.intellijplugin.core.ui.CustomComponentActionImpl
import io.confluent.intellijplugin.core.ui.filter.CountFilterPopupComponent
import io.confluent.intellijplugin.data.KafkaDataManager
import io.confluent.intellijplugin.model.TopicPresentable
import io.confluent.intellijplugin.model.TopicStatisticInfo
import io.confluent.intellijplugin.rfs.KafkaDriver
import io.confluent.intellijplugin.toolwindow.config.KafkaToolWindowSettings
import io.confluent.intellijplugin.util.KafkaDialogFactory
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import javax.swing.JLabel
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent

internal class TopicsController(val project: Project,
                       private val dataManager: KafkaDataManager,
                       private val mainController: KafkaMainController) : AbstractTableController<TopicPresentable>() {
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


  private val showInternalTopicsAction = object : DumbAwareToggleAction(KafkaMessagesBundle.message("show.internal.topic"), null,
                                                                        AllIcons.Actions.ToggleVisibility) {
    override fun isSelected(e: AnActionEvent) = KafkaToolWindowSettings.getInstance().showInternalTopics
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun setSelected(e: AnActionEvent, state: Boolean) {
      KafkaToolWindowSettings.getInstance().showInternalTopics = state
      dataManager.updater.invokeRefreshModel(dataManager.topicModel)
    }
  }

  private val showFavoriteTopicsAction = object : DumbAwareToggleAction(KafkaMessagesBundle.message("show.favorite.topic"), null,
                                                                        AllIcons.Nodes.Favorite) {
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
      sink[MainTreeController.DATA_MANAGER] = dataManager
      sink[MainTreeController.RFS_PATH] = getSelectedItem()?.name?.let { KafkaDriver.topicPath.child(it, false) }
    }

    dataManager.topicModel.addListener(object : DataModelListener {
      override fun onChanged() {
        infoPanel.text = TopicStatisticInfo.createFor(dataManager.getTopics()).toString()
      }

      override fun onError(msg: String, e: Throwable?) {
        infoPanel.text = TopicStatisticInfo.createFor(dataManager.getTopics()).toString()
      }
    })
  }

  override fun customTableInit(table: DataTable<TopicPresentable>) {
    FavoriteRenderer.installOnColumn(table, columnModel.getColumn(0)).apply {
      onClick = { row, _ ->
        val topic = table.getDataAt(row)
        topic?.let { dataManager.updatePinedTopics(it.name, !it.isFavorite) }
      }
    }

    LinkRenderer.installOnColumn(table, columnModel.getColumn(1)).apply {
      onClick = { row, _ ->
        val topicName = table.getDataAt(row)?.name
        topicName?.let {
          mainController.open(KafkaDriver.topicPath.child(it, false))
        }
      }
    }
  }

  override fun createTopLeftToolbarActions(): List<AnAction> {

    val countFilter = CountFilterPopupComponent(KafkaMessagesBundle.message("label.filter.limit"),
                                                KafkaToolWindowSettings.getInstance().getOrCreateConfig(
                                                  dataManager.connectionId).topicLimit)
    FilterAdapter.install(dataTable.tableModel, countFilter, LIMIT_FILTER) { limit ->
      val config = KafkaToolWindowSettings.getInstance().getOrCreateConfig(dataManager.connectionId)
      config.topicLimit = limit
      dataManager.updater.invokeRefreshModel(dataManager.topicModel)
    }

    return listOf(CustomComponentActionImpl(searchTextField),
                  CustomComponentActionImpl(countFilter),
                  showFavoriteTopicsAction,
                  showInternalTopicsAction)
  }

  override fun emptyTextProvider() = CustomEmptyTextProvider { emptyText: StatusText ->
    val toolWindowSettings = KafkaToolWindowSettings.getInstance()
    val clusterConfig = toolWindowSettings.getOrCreateConfig(dataManager.connectionId)
    if (!clusterConfig.topicFilterName.isNullOrBlank() || toolWindowSettings.showFavoriteTopics || toolWindowSettings.showInternalTopics) {
      emptyText.appendText(KafkaMessagesBundle.message("topics.empty.text.filter"), StatusText.DEFAULT_ATTRIBUTES)
      emptyText.appendSecondaryText(KafkaMessagesBundle.message("topics.empty.text.filter.additional"),
                                    SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) {
        clusterConfig.topicFilterName = null
        toolWindowSettings.showFavoriteTopics = false
        toolWindowSettings.showInternalTopics = false
        searchTextField.text = ""
        dataManager.updater.invokeRefreshModel(dataManager.topicModel)
      }
    }
    else {
      emptyText.appendText(KafkaMessagesBundle.message("topics.empty.text"), StatusText.DEFAULT_ATTRIBUTES)
      emptyText.appendLine(KafkaMessagesBundle.message("topics.text.create.link"),
                           SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) {
        KafkaDialogFactory.showCreateTopicDialog(dataManager)
      }
    }

    emptyText.isShowAboveCenter = false
  }

  override fun getColumnSettings() = KafkaToolWindowSettings.getInstance().topicColumnSettings
  override fun getRenderableColumns() = TopicPresentable.renderableColumns
  override fun getDataModel() = dataManager.topicModel
  override fun getAdditionalActions(): List<AnAction> = listOf()
  override fun showColumnFilter(): Boolean = false
  override fun getAdditionalContextActions(): List<AnAction> {
    val actionManager = ActionManager.getInstance()
    val group = actionManager.getAction("Kafka.Topic.Actions") as DefaultActionGroup
    return group.getChildren(actionManager).toList()
  }

  override fun createTopRightToolbarActions() = listOf(CustomComponentActionImpl(infoPanel))

  companion object {
    val LIMIT_FILTER = FilterKey("topicLimit")
  }
}