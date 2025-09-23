package io.confluent.kafka.core.monitoring.table.extension

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import io.confluent.kafka.core.monitoring.data.model.RemoteInfo
import io.confluent.kafka.core.monitoring.table.DataTable
import io.confluent.kafka.core.monitoring.table.TableEventListener
import io.confluent.kafka.core.settings.ColumnVisibilitySettings
import io.confluent.kafka.core.table.MaterialTableUtils

/**
 * Fits size of given table columns basing on guidelines.
 * Fitter could be optionally supplied with ColumnVisibilitySettings and when new column added, it will also fit this column.
 */
class TableColumnsFitter<T : RemoteInfo> private constructor(val table: DataTable<T>, private val columnSettings: ColumnVisibilitySettings?)
  : TableEventListener, Disposable {

  companion object {
    fun <T : RemoteInfo> installOn(table: DataTable<T>, columnSettings: ColumnVisibilitySettings?): TableColumnsFitter<T> {
      val tableColumnsFitter = TableColumnsFitter(table, columnSettings)
      Disposer.register(table, tableColumnsFitter)
      return tableColumnsFitter
    }

    fun get(table: DataTable<*>): TableColumnsFitter<*>? = table.tableModel.getFirstListenerOfClass(
      TableColumnsFitter::class.java) as? TableColumnsFitter<*>
  }

  /**
   * This flag prevents fits after the first data was received, but TableColumnsFitter still work for the case of column visibility change.
   */
  private var columnsFitted = false

  init {
    MaterialTableUtils.fitColumnsWidth(table)
    table.tableModel.addListener(this)
    columnSettings?.let { columnSettings.onColumnVisibilityChanged += ::onColumnVisibilityChanged }
  }

  /** If called, TableColumnsFitter will fit columns on next "onChanged" call. */
  fun reset() {
    columnsFitted = false
  }

  private fun onColumnVisibilityChanged(column: String, visible: Boolean) {
    // When we are adding new visible column, we will fit it.
    if (!visible) {
      return
    }

    for (i in 0 until table.columnModel.columnCount) {
      if (table.columnModel.getColumn(i).identifier == column) {
        MaterialTableUtils.fitColumnWidth(i, table)
        break
      }
    }
  }

  // region TableEventListener
  override fun onChanged() {
    if (columnsFitted || table.model.columnCount == 0) {
      return
    }

    MaterialTableUtils.fitColumnsWidth(table)

    if (table.rowCount != 0) {
      columnsFitted = true
    }
  }
  // endregion

  override fun dispose() {
    table.tableModel.removeListener(this)
    columnSettings?.let { columnSettings.onColumnVisibilityChanged -= ::onColumnVisibilityChanged }
  }
}
