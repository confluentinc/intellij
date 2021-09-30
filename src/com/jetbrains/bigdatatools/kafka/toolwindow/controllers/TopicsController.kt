package com.jetbrains.bigdatatools.kafka.toolwindow.controllers

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.OnePixelSplitter
import com.jetbrains.bigdatatools.kafka.common.editor.KafkaEditorProvider
import com.jetbrains.bigdatatools.kafka.common.models.KafkaEditorType
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.model.TopicPresentable
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.monitoring.table.DataTable
import com.jetbrains.bigdatatools.monitoring.table.DataTableCreator
import com.jetbrains.bigdatatools.monitoring.table.extension.TableExtensionType
import com.jetbrains.bigdatatools.monitoring.table.extension.TableSelectionPreserver
import com.jetbrains.bigdatatools.monitoring.table.model.DataTableColumnModel
import com.jetbrains.bigdatatools.monitoring.table.model.DataTableModel
import com.jetbrains.bigdatatools.settings.ColumnVisibilitySettings
import com.jetbrains.bigdatatools.table.MaterialJBScrollPane
import com.jetbrains.bigdatatools.util.BdIdeRegistryUtil
import com.jetbrains.bigdatatools.util.createActionToolbar
import java.util.*
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener

class TopicsController(private val project: Project, private val dataManager: KafkaDataManager) : Disposable {
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
                                                                TableExtensionType.LOADING_INDICATOR))
    TableSelectionPreserver.installOn(topicTable, null)
    topicTable.selectionModel.addListSelectionListener(topicSelectionListener)
    Disposer.register(this, topicTable)


    detailsSplitter.firstComponent = SimpleToolWindowPanel(false, true).apply {
      setContent(MaterialJBScrollPane(topicTable))
      val actionToolbar = createToolbar(columnModel)
      actionToolbar.setTargetComponent(this)
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
                                                                  AllIcons.Actions.ShowHiddens) {
      override fun isSelected(e: AnActionEvent) = settings.showInternalTopics
      override fun displayTextInToolbar() = false
      override fun setSelected(e: AnActionEvent, state: Boolean) {
        settings.showInternalTopics = state
        dataManager.autoUpdaterManager.reloadAsync(dataModel)

      }
    }

    val configStoragesColumnsAction = ColumnVisibilitySettings.createAction(columnModel.allColumns.map { it.name },
                                                                            settings.topicColumnSettings)

    val createProducer = object : DumbAwareAction(KafkaMessagesBundle.message("create.producer.action.title"),
                                                  null,
                                                  AllIcons.Actions.Upload) {
      override fun actionPerformed(e: AnActionEvent) {
        val file = LightVirtualFile("Kafka Producer")
        file.putUserData(KafkaEditorProvider.KAFKA_MANAGER_KEY, dataManager)
        file.putUserData(KafkaEditorProvider.KAFKA_EDITOR_TYPE, KafkaEditorType.PRODUCER)
        FileEditorManagerEx.getInstance(project).openFile(file, true)
      }
    }

    val createConsumer = object : DumbAwareAction(KafkaMessagesBundle.message("create.producer.action.title"),
                                                  null,
                                                  AllIcons.Actions.Download) {
      override fun actionPerformed(e: AnActionEvent) {
        val file = LightVirtualFile("Kafka Consumer")
        file.putUserData(KafkaEditorProvider.KAFKA_MANAGER_KEY, dataManager)
        file.putUserData(KafkaEditorProvider.KAFKA_EDITOR_TYPE, KafkaEditorType.CONSUMER)
        FileEditorManagerEx.getInstance(project).openFile(file, true)
      }
    }

    actions.add(showInternalTopicsAction)
    actions.add(configStoragesColumnsAction)

    if (BdIdeRegistryUtil.isInternalFeaturesAvailable()) {
      actions.add(createProducer)
      actions.add(createConsumer)
    }

    return createActionToolbar("BDTKafkaTopics", actions, false)
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