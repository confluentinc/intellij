package com.jetbrains.bigdatatools.kafka.toolwindow.controllers

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBScrollPane
import com.jetbrains.bigdatatools.monitoring.data.model.ObjectDataModel
import com.jetbrains.bigdatatools.monitoring.data.model.RemoteInfo
import com.jetbrains.bigdatatools.monitoring.table.DataTable
import com.jetbrains.bigdatatools.monitoring.table.DataTableCreator
import com.jetbrains.bigdatatools.monitoring.table.extension.TableExtensionType
import com.jetbrains.bigdatatools.monitoring.table.extension.TableHeightFitter
import com.jetbrains.bigdatatools.monitoring.table.extension.TableLoadingDecorator
import com.jetbrains.bigdatatools.monitoring.table.extension.TableSelectionPreserver
import com.jetbrains.bigdatatools.monitoring.table.model.DataTableColumnModel
import com.jetbrains.bigdatatools.monitoring.table.model.DataTableModel
import com.jetbrains.bigdatatools.settings.ColumnVisibilitySettings
import java.awt.BorderLayout
import java.util.*
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.reflect.KProperty1

abstract class AbstractTopicDetailController<T : RemoteInfo> : Disposable {
  private val columnModel: DataTableColumnModel<T>
  private var table: DataTable<T>
  private var tableScrollPane: JBScrollPane
  private val scrollPane: JBScrollPane

  protected var selectedId: String? = null

  init {
    columnModel = createColumnModel()
    table = createTable()
    tableScrollPane = createTableScrollPane()
    scrollPane = createComponent()
  }

  fun setTopicId(topicId: String) {
    selectedId = topicId

    val model = getModel(topicId)
    table.tableModel.setDataModel(model)
    if (model.size > 0) {
      TableHeightFitter.fitSize(tableScrollPane, table)
    }

    TableLoadingDecorator.installOn(table)
  }

  fun getComponent() = scrollPane

  override fun dispose() {}

  protected abstract fun getColumnSettings(): ColumnVisibilitySettings

  protected abstract fun getRenderableColumns(): List<KProperty1<T, *>>

  protected abstract fun getModel(topicId: String): ObjectDataModel<T>

  protected open fun getAdditionalActions(): List<AnAction> = emptyList()

  private fun createTable(): DataTable<T> {
    val tableModel = DataTableModel(null, columnModel)
    val table = DataTableCreator.create(tableModel, EnumSet.of(TableExtensionType.SPEED_SEARCH,
                                                               TableExtensionType.RENDERERS_SETTER,
                                                               TableExtensionType.ERROR_HANDLER,
                                                               TableExtensionType.LOADING_INDICATOR,
                                                               TableExtensionType.COLUMNS_FITTER))

    TableSelectionPreserver.installOn(table, null)
    Disposer.register(this, table)
    Disposer.register(table, columnModel)
    Disposer.register(table, tableModel)
    return table
  }

  private fun createColumnModel(): DataTableColumnModel<T> {
    val tasksColumnSettings = getColumnSettings()
    val renderableColumns = getRenderableColumns()
    return DataTableColumnModel(renderableColumns, tasksColumnSettings)
  }

  private fun createToolbar(columnModel: DataTableColumnModel<T>): JComponent {
    val actions = DefaultActionGroup()

    val configStoragesColumnsAction = ColumnVisibilitySettings.createAction(columnModel.allColumns.map { it.name },
                                                                            getColumnSettings())
    actions.add(configStoragesColumnsAction)

    val additionalActions = getAdditionalActions()
    additionalActions.forEach {
      actions.add(it)
    }

    return ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, actions, false).component
  }


  private fun createTableScrollPane(): JBScrollPane {
    val tableScrollPane = JBScrollPane(table)
    tableScrollPane.border = BorderFactory.createEmptyBorder()
    TableHeightFitter.installOn(tableScrollPane, table)
    return tableScrollPane
  }

  private fun createComponent(): JBScrollPane {
    val childPanel = SimpleToolWindowPanel(false, true).apply {
      setContent(tableScrollPane)
      toolbar = createToolbar(columnModel)
    }

    val panel = JPanel()
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
    panel.add(childPanel)

    val parentPanel = JPanel(BorderLayout(0, 10))
    parentPanel.add(panel, BorderLayout.NORTH)
    val scrollPane = JBScrollPane(parentPanel)
    scrollPane.border = BorderFactory.createEmptyBorder()
    return scrollPane
  }
}