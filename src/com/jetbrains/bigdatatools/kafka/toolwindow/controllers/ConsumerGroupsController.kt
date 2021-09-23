package com.jetbrains.bigdatatools.kafka.toolwindow.controllers

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.model.ConsumerGroupPresentable
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.monitoring.table.DataTableCreator
import com.jetbrains.bigdatatools.monitoring.table.extension.TableExtensionType
import com.jetbrains.bigdatatools.monitoring.table.extension.TableSelectionPreserver
import com.jetbrains.bigdatatools.monitoring.table.model.DataTableColumnModel
import com.jetbrains.bigdatatools.monitoring.table.model.DataTableModel
import com.jetbrains.bigdatatools.settings.ColumnVisibilitySettings
import com.jetbrains.bigdatatools.table.MaterialJBScrollPane
import com.jetbrains.bigdatatools.util.createActionToolbar
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
                                                               TableExtensionType.LOADING_INDICATOR))
    TableSelectionPreserver.installOn(table, null)

    Disposer.register(this, table)

    component = SimpleToolWindowPanel(false, true).apply {
      setContent(MaterialJBScrollPane(table))
      val actionToolbar = createToolbar(columnModel, columnSettings)
      actionToolbar.setTargetComponent(this)
      toolbar = actionToolbar.component
    }
  }

  private fun createToolbar(columnModel: DataTableColumnModel<ConsumerGroupPresentable>,
                            columnSettings: ColumnVisibilitySettings): ActionToolbar {

    val actions = DefaultActionGroup()
    actions.add(ColumnVisibilitySettings.createAction(columnModel.allColumns.map { it.name }, columnSettings))
    return createActionToolbar("BDTKafkaConsumerGroups", actions, false)
  }

  override fun dispose() {}

  fun getComponent() = component
}