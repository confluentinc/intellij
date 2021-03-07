package com.jetbrains.bigdatatools.kafka.toolwindow.controllers

import com.intellij.openapi.Disposable
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
import com.jetbrains.bigdatatools.monitoring.table.model.DataTableColumnModel
import com.jetbrains.bigdatatools.monitoring.table.model.DataTableModel
import com.jetbrains.bigdatatools.settings.ColumnVisibilitySettings
import java.awt.BorderLayout
import java.util.*
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JPanel
import kotlin.reflect.KProperty1

abstract class AbstractTopicDetailController<T : RemoteInfo> : Disposable {
  private val scrollPane: JBScrollPane

  private var table: DataTable<T>
  private var tableScrollPane: JBScrollPane

  init {
    table = createTable()
    tableScrollPane = createTableScrollPane()
    scrollPane = createComponent()
  }

  fun setTopicId(topicId: String) {
    val model = getModel(topicId)
    table.tableModel.setDataModel(model)
    if (model.size > 0) {
      TableHeightFitter.fitSize(tableScrollPane, table)
    }

    TableLoadingDecorator.installOn(table)
    tableScrollPane.revalidate()
    tableScrollPane.repaint()
  }

  fun getComponent() = scrollPane

  private fun createTable(): DataTable<T> {
    val tasksColumnSettings = getColumnSettings()

    val columnModel = DataTableColumnModel(getRenderableColumns(), tasksColumnSettings)
    val tableModel = DataTableModel(null, columnModel)

    val table = DataTableCreator.create(tableModel, EnumSet.of(TableExtensionType.SPEED_SEARCH,
                                                               TableExtensionType.RENDERERS_SETTER,
                                                               TableExtensionType.ERROR_HANDLER,
                                                               TableExtensionType.LOADING_INDICATOR,
                                                               TableExtensionType.COLUMNS_FITTER))
    Disposer.register(this, table)
    Disposer.register(table, columnModel)
    Disposer.register(table, tableModel)
    return table
  }

  override fun dispose() {}

  protected abstract fun getColumnSettings(): ColumnVisibilitySettings

  protected abstract fun getRenderableColumns(): List<KProperty1<T, *>>

  protected abstract fun getModel(topicId: String): ObjectDataModel<T>

  private fun createComponent(): JBScrollPane {
    val createdPanel = SimpleToolWindowPanel(false, true).apply {
      setContent(tableScrollPane)
    }

    val panel = JPanel()
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
    panel.add(createdPanel)

    val parentPanel = JPanel(BorderLayout(0, 10))
    parentPanel.add(panel, BorderLayout.NORTH)
    val scrollPane = JBScrollPane(parentPanel)
    scrollPane.border = BorderFactory.createEmptyBorder()
    return scrollPane
  }

  private fun createTableScrollPane(): JBScrollPane {
    val tableScrollPane = JBScrollPane(table)
    tableScrollPane.border = BorderFactory.createEmptyBorder()
    TableHeightFitter.installOn(tableScrollPane, table)
    return tableScrollPane
  }
}