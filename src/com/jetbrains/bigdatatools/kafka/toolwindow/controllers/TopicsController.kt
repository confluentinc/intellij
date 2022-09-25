package com.jetbrains.bigdatatools.kafka.toolwindow.controllers

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.OnePixelSplitter
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.model.TopicPresentable
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.kafka.util.KafkaDialogFactory
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.common.monitoring.table.DataTable
import com.jetbrains.bigdatatools.common.monitoring.table.DataTableCreator
import com.jetbrains.bigdatatools.common.monitoring.table.extension.TableExtensionType
import com.jetbrains.bigdatatools.common.monitoring.table.extension.TableSelectionPreserver
import com.jetbrains.bigdatatools.common.monitoring.table.model.DataTableColumnModel
import com.jetbrains.bigdatatools.common.monitoring.table.model.DataTableModel
import com.jetbrains.bigdatatools.common.settings.ColumnVisibilitySettings
import com.jetbrains.bigdatatools.common.table.MaterialJBScrollPane
import com.jetbrains.bigdatatools.common.table.filters.TableFilterHeader
import com.jetbrains.bigdatatools.common.util.ToolbarUtils
import java.util.*
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener

class TopicsController(val project: Project, private val dataManager: KafkaDataManager) : Disposable {
  private val detailsSplitter: OnePixelSplitter = OnePixelSplitter()

  private val dataModel = dataManager.topicModel
  private val topicTable: DataTable<TopicPresentable>

  private val topicDetailsController = TopicDetailsController(project, dataManager).also {
    Disposer.register(this, it)
  }

  private val topicSelectionListener = object : ListSelectionListener {
    override fun valueChanged(e: ListSelectionEvent) {
      if (e.valueIsAdjusting)
        return
      showTopicDetails()
    }
  }

  init {
    val columnSettings = KafkaToolWindowSettings.getInstance().topicColumnSettings
    val columnModel = DataTableColumnModel(TopicPresentable.renderableColumns, columnSettings)
    val tableModel = DataTableModel(dataModel, columnModel)

    topicTable = DataTableCreator.create(tableModel, EnumSet.of(TableExtensionType.SPEED_SEARCH,
                                                                TableExtensionType.RENDERERS_SETTER,
                                                                TableExtensionType.COLUMNS_FITTER,
                                                                TableExtensionType.ERROR_HANDLER,
                                                                TableExtensionType.SELECTION_PRESERVER,
                                                                TableExtensionType.LOADING_INDICATOR,
                                                                TableExtensionType.MULTI_SELECT))
    TableSelectionPreserver.installOn(topicTable, null)
    TableFilterHeader(topicTable).apply {
      caseInsensitive = true
    }
    topicTable.selectionModel.addListSelectionListener(topicSelectionListener)
    Disposer.register(this, topicTable)

    detailsSplitter.firstComponent = SimpleToolWindowPanel(false, true).apply {
      setContent(MaterialJBScrollPane(topicTable))
      val actionToolbar = createToolbar(columnModel)
      actionToolbar.targetComponent = this
      toolbar = actionToolbar.component
    }
    detailsSplitter.secondComponent = topicDetailsController.getComponent()
  }

  override fun dispose() {}

  fun getComponent() = detailsSplitter

  private fun createToolbar(columnModel: DataTableColumnModel<TopicPresentable>): ActionToolbar {
    val settings = KafkaToolWindowSettings.getInstance()

    val actions = DefaultActionGroup()

    val showInternalTopicsAction = object : DumbAwareToggleAction(KafkaMessagesBundle.message("show.internal.topic"),
                                                                  KafkaMessagesBundle.message("show.internal.topic.hint"),
                                                                  AllIcons.Actions.ToggleVisibility) {
      override fun isSelected(e: AnActionEvent) = settings.showInternalTopics
      override fun getActionUpdateThread() = ActionUpdateThread.BGT
      override fun displayTextInToolbar() = false
      override fun setSelected(e: AnActionEvent, state: Boolean) {
        settings.showInternalTopics = state
        dataManager.autoUpdaterManager.reloadAsync(dataModel)
      }
    }

    val createTopicAction = object : DumbAwareAction(KafkaMessagesBundle.message("action.create.topic"),
                                                     null,
                                                     AllIcons.General.Add) {
      override fun actionPerformed(e: AnActionEvent) {
        KafkaDialogFactory.showCreateTopicDialog(dataManager)
      }

      override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = dataManager.client.isConnected()
      }

      override fun getActionUpdateThread() = ActionUpdateThread.BGT
    }

    @Suppress("DialogTitleCapitalization")
    val deleteTopicAction = object : DumbAwareAction(KafkaMessagesBundle.message("action.delete.topic"),
                                                     null,
                                                     AllIcons.General.Remove) {
      override fun actionPerformed(e: AnActionEvent) {
        val selectedRows = topicTable.selectedRows

        val selectedNames = selectedRows.map {
          val modelIndex = topicTable.convertRowIndexToModel(it)
          topicTable.tableModel.getInfoAt(modelIndex)?.name
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
        e.presentation.isEnabled = dataManager.client.isConnected() && topicTable.selectedRow != -1
      }

      override fun getActionUpdateThread() = ActionUpdateThread.BGT
    }

    val configStoragesColumnsAction = ColumnVisibilitySettings.createAction(columnModel.allColumns.map { it.name },
                                                                            settings.topicColumnSettings)

    actions.add(showInternalTopicsAction)
    actions.add(configStoragesColumnsAction)

    actions.add(Separator())

    actions.add(createTopicAction)
    actions.add(deleteTopicAction)

    return ToolbarUtils.createActionToolbar("BDTKafkaTopics", actions, false)
  }

  private fun showTopicDetails() {
    if (topicTable.selectedRow == -1) {
      return
    }
    val modelIndex = topicTable.convertRowIndexToModel(topicTable.selectedRow)
    val selectedTopicName = topicTable.tableModel.getInfoAt(modelIndex)?.name ?: return

    topicDetailsController.setDetailsId(selectedTopicName)

    val settings = KafkaToolWindowSettings.getInstance()
    settings.setSelectedTopicName(dataManager.connectionId, selectedTopicName)
  }
}