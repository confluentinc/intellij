package io.confluent.kafka.core.monitoring.table.extension

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import io.confluent.kafka.core.monitoring.data.model.RemoteInfo
import io.confluent.kafka.core.monitoring.table.DataTable
import io.confluent.kafka.core.monitoring.table.TableEventListener

/**
 * Autoselect immediately if table is not empty. If empty, awaits first data come and autoselects if possible.
 * If idToSelect is null, autoselects first row, else selects specified id if such id is present in table.
 */
class TableRowAutoselector<T : RemoteInfo> private constructor(private val table: DataTable<T>, private val idToSelect: String? = null)
  : TableEventListener, Disposable {

  init {
    table.tableModel.addListener(this)
  }

  // region TableEventListener
  override fun onChanged() {
    if (table.rowCount > 0) {
      performSelection(table, idToSelect)
      Disposer.dispose(this)
      return
    }
  }
  // endregion

  override fun dispose() {
    table.tableModel.removeListener(this)
  }

  companion object {
    fun <T : RemoteInfo> installOn(table: DataTable<T>, idToSelect: String? = null) {

      if (Disposer.isDisposed(table)) return

      if (table.tableModel.hasListenerOfClass(this.javaClass)) return

      if (table.isEmpty) {
        val rowAutoselector = TableRowAutoselector(table, idToSelect)
        Disposer.register(table, rowAutoselector)
      }
      else {
        performSelection(table, idToSelect)
      }
    }

    fun <T : RemoteInfo> performSelection(table: DataTable<T>, idToSelect: String? = null) {

      if (table.isEmpty) {
        return
      }

      val modelIndex = if (idToSelect == null) null else table.tableModel.getIndexById(idToSelect)

      if (modelIndex == null) {
        table.setRowSelectionInterval(0, 0)
      }
      else {
        try {
          val viewIndex = table.convertRowIndexToView(modelIndex)
          if (viewIndex != -1) {
            table.setRowSelectionInterval(viewIndex, viewIndex)
          }
        }
        catch (e: IndexOutOfBoundsException) {
        }
      }
    }
  }
}
