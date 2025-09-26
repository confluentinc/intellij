package io.confluent.intellijplugin.core.table.extension

import javax.swing.JTable
import javax.swing.event.TableModelEvent
import javax.swing.event.TableModelListener

/** Calls action when supplied table gets first data row. */
class TableFirstRowAdded(private val table: JTable, private val action: () -> Unit) : TableModelListener {

  init {
    if (table.rowCount != 0) {
      tableChanged(null)
    }
    else {
      table.model.addTableModelListener(this)
    }
  }

  // region TableModelListener
  override fun tableChanged(e: TableModelEvent?) {
    if (table.rowCount != 0) {
      table.model.removeTableModelListener(this)
      action()
    }
  }
  // endregion
}