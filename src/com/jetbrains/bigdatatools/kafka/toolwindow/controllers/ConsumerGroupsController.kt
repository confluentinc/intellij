package com.jetbrains.bigdatatools.kafka.toolwindow.controllers

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.jetbrains.bigdatatools.common.monitoring.data.model.FilterAdapter
import com.jetbrains.bigdatatools.common.monitoring.data.model.FilterKey
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.AbstractTableController
import com.jetbrains.bigdatatools.common.ui.CustomComponentActionImpl
import com.jetbrains.bigdatatools.common.ui.filter.CountFilterPopupComponent
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.model.ConsumerGroupPresentable
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import javax.swing.event.DocumentEvent

class ConsumerGroupsController(val dataManager: KafkaDataManager) : AbstractTableController<ConsumerGroupPresentable>() {
  init {
    init()
  }

  override fun getColumnSettings() = KafkaToolWindowSettings.getInstance().consumerGroupsColumnSettings

  override fun getRenderableColumns() = ConsumerGroupPresentable.renderableColumns

  override fun showColumnFilter(): Boolean = false

  override fun getDataModel() = dataManager.consumerGroupsModel

  override fun createTopLeftToolbarActions(): List<AnAction> {
    val searchTextField = SearchTextField(false).apply {
      addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
          val config = KafkaToolWindowSettings.getInstance().getOrCreateConfig(dataManager.connectionId)
          config.consumerFilterName = this@apply.text
          dataManager.updater.invokeRefreshModel(dataManager.consumerGroupsModel)
        }
      })

      text = KafkaToolWindowSettings.getInstance().getOrCreateConfig(dataManager.connectionId).consumerFilterName ?: ""
    }

    val countFilter = CountFilterPopupComponent(KafkaMessagesBundle.message("label.filter.limit"),
                                                KafkaToolWindowSettings.getInstance().getOrCreateConfig(
                                                  dataManager.connectionId).consumerLimit)
    FilterAdapter.install(dataTable.tableModel, countFilter, LIMIT_FILTER) { limit ->
      val config = KafkaToolWindowSettings.getInstance().getOrCreateConfig(dataManager.connectionId)
      config.consumerLimit = limit
      dataManager.updater.invokeRefreshModel(dataManager.consumerGroupsModel)
    }

    return listOf(CustomComponentActionImpl(searchTextField), CustomComponentActionImpl(countFilter))
  }

  companion object {
    val LIMIT_FILTER = FilterKey("consumersLimit")
  }
}