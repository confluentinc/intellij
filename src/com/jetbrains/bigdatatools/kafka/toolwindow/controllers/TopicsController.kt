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
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.TableWithDetailsMonitoringController
import com.jetbrains.bigdatatools.common.ui.CustomComponentActionImpl
import com.jetbrains.bigdatatools.common.util.ToolbarUtils
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.model.TopicPresentable
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.kafka.util.KafkaDialogFactory
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import javax.swing.event.DocumentEvent

class TopicsController(val project: Project,
                       private val dataManager: KafkaDataManager) : TableWithDetailsMonitoringController<TopicPresentable, String>() {
  override val detailsController = TopicDetailsController(project, dataManager)

  private val showInternalTopicsAction = object : DumbAwareToggleAction(KafkaMessagesBundle.message("show.internal.topic"),
                                                                        KafkaMessagesBundle.message("show.internal.topic.hint"),
                                                                        AllIcons.Actions.ToggleVisibility) {
    override fun isSelected(e: AnActionEvent) = KafkaToolWindowSettings.getInstance().showInternalTopics
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun displayTextInToolbar() = false
    override fun setSelected(e: AnActionEvent, state: Boolean) {
      KafkaToolWindowSettings.getInstance().showInternalTopics = state
      dataManager.updater.invokeRefreshModel(dataManager.topicModel)
    }
  }

  private val createTopicAction = object : DumbAwareAction(KafkaMessagesBundle.message("action.create.topic"),
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
  private val deleteTopicAction = object : DumbAwareAction(KafkaMessagesBundle.message("action.delete.topic"),
                                                           null,
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
  }

  override fun createTopToolBar(): ActionToolbar {
    val searchTextField = SearchTextField(false).apply {
      addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
          val config = KafkaToolWindowSettings.getInstance().getOrCreateConfig(dataManager.connectionId)
          config.topicFilterName = this@apply.text
          dataManager.updater.invokeRefreshModel(dataManager.topicModel)
        }
      })
    }

    val toolbar = DefaultActionGroup(CustomComponentActionImpl(searchTextField), showInternalTopicsAction, Separator(), createTopicAction,
                                     deleteTopicAction)
    return ToolbarUtils.createActionToolbar("BDTKafkaTopicsTopToolbar", toolbar, true)
  }

  override fun getColumnSettings() = KafkaToolWindowSettings.getInstance().topicColumnSettings

  override fun getRenderableColumns() = TopicPresentable.renderableColumns

  override fun getDataModel() = dataManager.topicModel

  override fun getAdditionalActions(): List<AnAction> = listOf()

  override fun showColumnFilter(): Boolean = false

  override fun indexToDetailId(modelIndex: Int) = dataTable.tableModel.getInfoAt(modelIndex)?.name

  override fun saveSelectedItem() {}
}