package com.jetbrains.bigdatatools.kafka.producer.editor

import com.intellij.ui.TableUtil
import com.intellij.ui.ToolbarDecorator
import com.jetbrains.bigdatatools.settings.components.BdtPropertyComponent
import com.jetbrains.bigdatatools.settings.connections.Property
import com.jetbrains.bigdatatools.table.MaterialTable
import javax.swing.JTable

class PropertiesTable(data: String) {
  private val tableModel = PropertiesTableModel(BdtPropertyComponent.parseProperties(data).toMutableList())
  private val table = MaterialTable(tableModel, tableModel.columnModel).apply { autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS }
  private val component = createDecoratedTable()

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

  fun setProperties(new: List<Property>) {
    val oldSize = tableModel.properties.size
    repeat(oldSize) {
      tableModel.removeRow(0)
    }
    new.forEach {
      tableModel.addRow(it)
    }
  }

  fun getComponent() = component
  fun getProperties(): List<Property> = tableModel.properties
}