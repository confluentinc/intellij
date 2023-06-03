package com.jetbrains.bigdatatools.kafka.toolwindow.controllers

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.UIUtil
import com.jetbrains.bigdatatools.common.monitoring.data.listener.DataModelListener
import com.jetbrains.bigdatatools.common.monitoring.data.model.FilterAdapter
import com.jetbrains.bigdatatools.common.monitoring.data.model.FilterKey
import com.jetbrains.bigdatatools.common.monitoring.table.DataTable
import com.jetbrains.bigdatatools.common.monitoring.table.extension.CustomEmptyTextProvider
import com.jetbrains.bigdatatools.common.monitoring.table.model.DataTableModel
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.AbstractTableController
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.MainTreeController
import com.jetbrains.bigdatatools.common.table.renderers.LinkRenderer
import com.jetbrains.bigdatatools.common.ui.CustomComponentActionImpl
import com.jetbrains.bigdatatools.common.ui.filter.CountFilterPopupComponent
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.model.TopicPresentable
import com.jetbrains.bigdatatools.kafka.model.TopicStatisticInfo
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.kafka.util.KafkaDialogFactory
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import javax.swing.JLabel
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent

class TopicsController(val project: Project,
                       private val dataManager: KafkaDataManager,
                       private val mainController: KafkaMainController) : AbstractTableController<TopicPresentable>() {
  val infoPanel = JLabel("").apply {
    foreground = UIUtil.getLabelInfoForeground()
    font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
  }


  private val showInternalTopicsAction = object : DumbAwareToggleAction(KafkaMessagesBundle.message("show.internal.topic"), null,
                                                                        AllIcons.Actions.ToggleVisibility) {
    override fun isSelected(e: AnActionEvent) = KafkaToolWindowSettings.getInstance().showInternalTopics
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun displayTextInToolbar() = false
    override fun setSelected(e: AnActionEvent, state: Boolean) {
      KafkaToolWindowSettings.getInstance().showInternalTopics = state
      dataManager.updater.invokeRefreshModel(dataManager.topicModel)
    }
  }

  private val clearTopicAction = ActionManager.getInstance().getAction("kafka.ClearTopicAction")
  private val createTopicAction = object : DumbAwareAction(KafkaMessagesBundle.message("action.kafka.CreateTopicAction.text"), null,
                                                           AllIcons.General.Add) {
    override fun actionPerformed(e: AnActionEvent) {
      KafkaDialogFactory.showCreateTopicDialog(dataManager)
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = dataManager.client.isConnected()
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
  }

  private val deleteTopicAction = object : DumbAwareAction(KafkaMessagesBundle.message("action.kafka.DeleteTopicAction.text"), null,
                                                           AllIcons.General.Remove) {
    override fun actionPerformed(e: AnActionEvent) {
      val selectedRows = dataTable.selectedRows

      val selectedNames = selectedRows.map {
        val modelIndex = dataTable.convertRowIndexToModel(it)
        dataTable.tableModel.getInfoAt(modelIndex)?.name
      }.mapNotNull { it }

      if (selectedNames.isEmpty()) {
        return
      }

      val msg = if (selectedNames.size == 1)
        KafkaMessagesBundle.message("action.delete.topic.single.message", selectedNames.first())
      else
        KafkaMessagesBundle.message("action.delete.topic.multi.message", selectedNames.size)

      val res = Messages.showOkCancelDialog(project,
                                            msg,
                                            KafkaMessagesBundle.message("action.delete.topic.title"),
                                            CommonBundle.getOkButtonText(),
                                            CommonBundle.getCancelButtonText(),
                                            Messages.getQuestionIcon())
      if (res != Messages.OK)
        return
      dataManager.deleteTopic(selectedNames)
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = dataManager.client.isConnected() && getSelectedItem() != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
  }

  init {
    init()
    dataTable.selectionModel.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION

    dataTable.customDataProvider = DataProvider { dataId ->
      when {
        MainTreeController.DATA_MANAGER.`is`(dataId) -> dataManager
        MainTreeController.RFS_PATH.`is`(dataId) -> getSelectedItem()?.name?.let { KafkaDriver.topicPath.child(it, false) }
        else -> null
      }
    }

    dataManager.topicModel.addListener(object : DataModelListener {
      override fun onChanged() {
        super.onChanged()

        infoPanel.text = TopicStatisticInfo.createFor(dataManager.getTopics()).toString()
      }

      override fun onError(msg: String, e: Throwable?) {
        infoPanel.text = TopicStatisticInfo.createFor(dataManager.getTopics()).toString()

      }
    })
  }

  override fun customTableInit(table: DataTable<TopicPresentable>) {
    LinkRenderer.installOnColumn(table, columnModel.getColumn(0)).apply {
      onClick = { row, _ ->
        @Suppress("UNCHECKED_CAST")
        val topicName = (table.model as? DataTableModel<TopicPresentable>)?.getInfoAt(row)?.name
        topicName?.let {
          mainController.open(KafkaDriver.topicPath.child(it, false))
        }
      }
    }
  }

  override fun createTopLeftToolbarActions(): List<AnAction> {
    val searchTextField = SearchTextField(false).apply {
      addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
          val config = KafkaToolWindowSettings.getInstance().getOrCreateConfig(dataManager.connectionId)
          config.topicFilterName = this@apply.text
          dataManager.updater.invokeRefreshModel(dataManager.topicModel)
        }
      })
    }

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
                  showInternalTopicsAction)
  }

  override fun emptyTextProvider() = CustomEmptyTextProvider { emptyText: StatusText ->
    val clusterConfig = KafkaToolWindowSettings.getInstance().getOrCreateConfig(dataManager.connectionId)
    if (clusterConfig.topicFilterName.isNullOrBlank()) {
      emptyText.appendText(KafkaMessagesBundle.message("topics.empty.text"), StatusText.DEFAULT_ATTRIBUTES)
      emptyText.appendLine(KafkaMessagesBundle.message("topics.text.create.link"),
                           SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) {
        KafkaDialogFactory.showCreateTopicDialog(dataManager)
      }
    }
    else {
      emptyText.appendText(KafkaMessagesBundle.message("topics.empty.text.filter"), StatusText.DEFAULT_ATTRIBUTES)
      emptyText.appendSecondaryText(KafkaMessagesBundle.message("topics.empty.text.filter.additional"),
                                    SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) {
        clusterConfig.topicFilterName = null
        dataManager.updater.invokeRefreshModel(dataManager.topicModel)
      }
    }

    emptyText.isShowAboveCenter = false
  }

  override fun getColumnSettings() = KafkaToolWindowSettings.getInstance().topicColumnSettings
  override fun getRenderableColumns() = TopicPresentable.renderableColumns
  override fun getDataModel() = dataManager.topicModel
  override fun getAdditionalActions(): List<AnAction> = listOf()
  override fun showColumnFilter(): Boolean = false
  override fun getAdditionalContextActions(): List<AnAction> = listOf(createTopicAction, deleteTopicAction, clearTopicAction)
  override fun createTopRightToolbarActions() = listOf(CustomComponentActionImpl(infoPanel))

  companion object {
    val LIMIT_FILTER = FilterKey("topicLimit")
  }
}