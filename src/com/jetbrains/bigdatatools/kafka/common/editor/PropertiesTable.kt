package com.jetbrains.bigdatatools.kafka.common.editor

import com.intellij.ui.JBColor
import com.intellij.ui.TableUtil
import com.intellij.ui.ToolbarDecorator
import com.jetbrains.bigdatatools.core.settings.components.BdtPropertyComponent
import com.jetbrains.bigdatatools.core.settings.connections.Property
import com.jetbrains.bigdatatools.core.table.MaterialTable
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.JTable

class PropertiesTable(data: List<Property>, val isEditable: Boolean = true) {
  constructor(data: String) : this(BdtPropertyComponent.parseProperties(data))

  private val tableModel = PropertiesTableModel(data.toMutableList(), isEditable)
  val table = MaterialTable(tableModel, tableModel.columnModel).apply {
    autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
    tableHeader.border = BorderFactory.createEmptyBorder()
    background = JBColor.WHITE
    tableHeader.background = JBColor.WHITE
  }
  private val component = createDecoratedTable()

  var properties: MutableList<Property>
    get() = tableModel.properties
    set(value) {
      tableModel.properties = value
    }

  private fun createDecoratedTable(): JPanel {
    val createDecorator = ToolbarDecorator.createDecorator(table)
    if (isEditable) {
      createDecorator.setAddAction {
        tableModel.addRow(Property("", ""))
        val tableIndex = table.convertRowIndexToView(tableModel.rowCount - 1)
        table.setRowSelectionInterval(tableIndex, tableIndex)
        table.editCellAt(tableIndex, 0)
        TableUtil.scrollSelectionToVisible(table)
      }.setRemoveAction {
        if (table.selectedRow != -1) {
          val modelIndex = table.convertRowIndexToModel(table.selectedRow)
          tableModel.removeRow(modelIndex)
        }
      }
    }

    return createDecorator.setScrollPaneBorder(BorderFactory.createEmptyBorder()).createPanel()
  }

  fun clear() {
    tableModel.clear()
  }

  fun getComponent() = component
}