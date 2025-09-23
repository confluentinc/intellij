package io.confluent.kafka.core.rfs.editorviewer

import io.confluent.kafka.core.rfs.driver.Driver
import io.confluent.kafka.core.rfs.search.impl.ListElement
import io.confluent.kafka.util.KafkaMessagesBundle
import javax.swing.event.TableModelEvent
import javax.swing.event.TableModelListener
import javax.swing.table.TableModel
import kotlin.math.max
import kotlin.math.min

class RfsTableModel(val driver: Driver, val columns: List<RfsTableColumn<*>>) : TableModel {

  private var data = mutableListOf<ListElement>()

  private var listeners = emptyList<TableModelListener>()

  fun clear() {
    if (data.isNotEmpty()) {
      val dataSize = data.size
      data.clear()
      listeners.forEach {
        it.tableChanged(TableModelEvent(this, 0, dataSize - 1, TableModelEvent.ALL_COLUMNS, TableModelEvent.DELETE))
      }
    }
  }

  fun setData(data: MutableList<ListElement>) {
    val oldSize = this.data.size
    this.data = data
    if (oldSize > data.size) {
      listeners.forEach {
        it.tableChanged(TableModelEvent(this, max(0, data.size - 1), oldSize - 1, TableModelEvent.ALL_COLUMNS, TableModelEvent.DELETE))
      }
    }
    else if (data.size > oldSize) {
      listeners.forEach {
        it.tableChanged(TableModelEvent(this, max(0, oldSize - 1), data.size - 1, TableModelEvent.ALL_COLUMNS, TableModelEvent.INSERT))
      }
    }

    val changedSize = min(oldSize - 1, data.size - 1)
    if (changedSize > 0) {
      listeners.forEach {
        it.tableChanged(TableModelEvent(this, 0, min(oldSize - 1, data.size - 1), TableModelEvent.ALL_COLUMNS, TableModelEvent.UPDATE))
      }
    }
  }

  fun addData(data: List<ListElement>) {
    if (data.isEmpty()) {
      return
    }
    val oldSize = this.data.size
    this.data.addAll(data)
    val newSize = oldSize + data.size - 1
    listeners.forEach {
      it.tableChanged(TableModelEvent(this, oldSize, newSize, TableModelEvent.ALL_COLUMNS, TableModelEvent.INSERT))
    }
  }

  fun remove(element: ListElement) {
    val index = data.indexOf(element)
    if (index == -1) {
      return
    }
    data.remove(element)
    listeners.forEach {
      it.tableChanged(TableModelEvent(this, index, index, TableModelEvent.ALL_COLUMNS, TableModelEvent.DELETE))
    }
  }

  override fun getRowCount() = data.size

  override fun getColumnCount() = columns.size + 1

  override fun getColumnName(columnIndex: Int): String {
    return when (columnIndex) {
      0 -> KafkaMessagesBundle.message("file.viewer.column.name")
      else -> columns[columnIndex - 1].name
    }
  }

  override fun getColumnClass(columnIndex: Int): Class<*> {
    return if (getColumnName(columnIndex) == "Size")
      Long::class.java
    else
      Any::class.java
  }

  override fun isCellEditable(rowIndex: Int, columnIndex: Int) = false

  fun getEntry(rowIndex: Int) = if (rowIndex >= data.size) null else data[rowIndex]

  override fun getValueAt(rowIndex: Int, columnIndex: Int): ListElement {
    return data[rowIndex]
  }

  override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) = Unit

  override fun addTableModelListener(listener: TableModelListener) {
    listeners = listeners + listener
  }

  override fun removeTableModelListener(listener: TableModelListener) {
    listeners = listeners - listener
  }
}