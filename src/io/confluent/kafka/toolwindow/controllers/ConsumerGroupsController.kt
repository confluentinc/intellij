package io.confluent.kafka.toolwindow.controllers

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import io.confluent.kafka.core.monitoring.data.model.FilterAdapter
import io.confluent.kafka.core.monitoring.data.model.FilterKey
import io.confluent.kafka.core.monitoring.table.DataTable
import io.confluent.kafka.core.monitoring.toolwindow.AbstractTableController
import io.confluent.kafka.core.monitoring.toolwindow.MainTreeController
import io.confluent.kafka.core.table.renderers.FavoriteRenderer
import io.confluent.kafka.core.table.renderers.LinkRenderer
import io.confluent.kafka.core.ui.CustomComponentActionImpl
import io.confluent.kafka.core.ui.filter.CountFilterPopupComponent
import io.confluent.kafka.data.KafkaDataManager
import io.confluent.kafka.model.ConsumerGroupPresentable
import io.confluent.kafka.rfs.KafkaDriver
import io.confluent.kafka.toolwindow.config.KafkaToolWindowSettings
import io.confluent.kafka.util.KafkaMessagesBundle
import javax.swing.event.DocumentEvent

internal class ConsumerGroupsController(val dataManager: KafkaDataManager,
                               private val mainController: KafkaMainController) : AbstractTableController<ConsumerGroupPresentable>() {
  init {
    init()

    dataTable.customDataProvider = UiDataProvider { sink ->
      sink[MainTreeController.DATA_MANAGER] = dataManager
      sink[MainTreeController.RFS_PATH] = getSelectedItem()?.consumerGroup
        ?.let { KafkaDriver.consumerPath.child(it, false) }
    }
  }


  override fun customTableInit(table: DataTable<ConsumerGroupPresentable>) {
    FavoriteRenderer.installOnColumn(table, columnModel.getColumn(0)).apply {
      onClick = { row, _ ->
        val consumerGroupPresentable = table.getDataAt(row)
        consumerGroupPresentable?.let { dataManager.updatePinedConsumerGroups(it.consumerGroup, !it.isFavorite) }
      }
    }


    LinkRenderer.installOnColumn(table, columnModel.getColumn(1)).apply {
      onClick = { row, _ ->
        val schema = table.getDataAt(row)?.consumerGroup
        schema?.let {
          mainController.open(KafkaDriver.consumerPath.child(it, false))
        }
      }
    }
  }

  override fun getAdditionalContextActions(): List<AnAction> {
    val actionManager = ActionManager.getInstance()
    val group = actionManager.getAction("Kafka.Consumer.Group.Actions") as DefaultActionGroup
    return group.getChildren(actionManager).toList()
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