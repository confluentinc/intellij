package com.jetbrains.bigdatatools.kafka.producer.editor

import com.intellij.ui.TableUtil
import com.intellij.ui.ToolbarDecorator
import com.jetbrains.bigdatatools.settings.components.BdtPropertyComponent
import com.jetbrains.bigdatatools.settings.connections.Property
import com.jetbrains.bigdatatools.table.MaterialTable
import javax.swing.JTable

class PropertiesTable(data: List<Property>) {

  constructor(data: String) : this(BdtPropertyComponent.parseProperties(data))

  private val tableModel = PropertiesTableModel(data.toMutableList())
  private val table = MaterialTable(tableModel, tableModel.columnModel).apply { autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS }
  private val component = createDecoratedTable()

  var properties: MutableList<Property>
    get() = tableModel.properties
    set(value) {
      tableModel.properties = value
    }

  private fun createDecoratedTable() = ToolbarDecorator.createDecorator(table).setAddAction {
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
  }.createPanel()

  fun clear() {
    tableModel.clear()
  }

  fun getComponent() = component
}