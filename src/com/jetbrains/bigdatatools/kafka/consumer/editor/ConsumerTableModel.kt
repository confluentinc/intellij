package com.jetbrains.bigdatatools.kafka.consumer.editor

import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableColumnModel
import javax.swing.table.TableColumn
import javax.swing.table.TableColumnModel

class ConsumerTableModel<T>(private val data: MutableList<T>,
                            private val columnNames: List<String>,
                            private val columnMapper: (T, Int) -> Any) : AbstractTableModel() {

  val columnModel: TableColumnModel by lazy {
    DefaultTableColumnModel().apply {
      columnNames.forEachIndexed { index, name ->
        addColumn(TableColumn(index).apply { headerValue = name })
      }
    }
  }

  override fun getRowCount() = data.size
  override fun getColumnCount() = columnNames.size
  override fun getValueAt(rowIndex: Int, columnIndex: Int) = columnMapper(data[rowIndex], columnIndex)
  fun getValueAt(rowIndex: Int): T? = if (rowIndex in data.indices) data[rowIndex] else null

  fun clear() {
    data.clear()
    fireTableRowsDeleted(0, data.size - 1)
  }

  fun addElement(element: T) {
    data += element
    fireTableRowsInserted(data.size - 1, data.size - 1)
  }

  fun elements(): List<T> = data
}