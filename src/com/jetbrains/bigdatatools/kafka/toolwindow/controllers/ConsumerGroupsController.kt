package com.jetbrains.bigdatatools.kafka.toolwindow.controllers

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.jetbrains.bigdatatools.common.monitoring.table.DataTableCreator
import com.jetbrains.bigdatatools.common.monitoring.table.extension.TableExtensionType
import com.jetbrains.bigdatatools.common.monitoring.table.extension.TableSelectionPreserver
import com.jetbrains.bigdatatools.common.monitoring.table.model.DataTableColumnModel
import com.jetbrains.bigdatatools.common.monitoring.table.model.DataTableModel
import com.jetbrains.bigdatatools.common.table.MaterialJBScrollPane
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.model.ConsumerGroupPresentable
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import java.util.*
import javax.swing.JComponent

class ConsumerGroupsController(dataManager: KafkaDataManager) : Disposable {
  private val component: JComponent

  init {
    val dataModel = dataManager.consumerGroupsModel

    val columnSettings = KafkaToolWindowSettings.getInstance().consumerGroupsColumnSettings

    val columnModel = DataTableColumnModel(ConsumerGroupPresentable.renderableColumns, columnSettings)
    val tableModel = DataTableModel(dataModel, columnModel)

    val table = DataTableCreator.create(tableModel, EnumSet.of(TableExtensionType.SPEED_SEARCH,
                                                               TableExtensionType.RENDERERS_SETTER,
                                                               TableExtensionType.COLUMNS_FITTER,
                                                               TableExtensionType.ERROR_HANDLER,
                                                               TableExtensionType.SELECTION_PRESERVER,
                                                               TableExtensionType.LOADING_INDICATOR,
                                                               TableExtensionType.SMART_RESIZER))
    TableSelectionPreserver.installOn(table, null)

    Disposer.register(this, table)

    component = SimpleToolWindowPanel(false, true).apply {
      setContent(MaterialJBScrollPane(table))
    }
  }

  override fun dispose() = Unit

  fun getComponent() = component
}