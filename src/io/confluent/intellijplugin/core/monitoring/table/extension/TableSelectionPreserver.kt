package io.confluent.intellijplugin.core.monitoring.table.extension

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import io.confluent.intellijplugin.core.monitoring.data.model.RemoteInfo
import io.confluent.intellijplugin.core.monitoring.table.DataTable
import io.confluent.intellijplugin.core.monitoring.table.TableEventListener
import io.confluent.intellijplugin.core.monitoring.table.getSelectedData

/** Saves and restores table selection interval during data reload. */
class TableSelectionPreserver<T : RemoteInfo> private constructor(private val table: DataTable<T>, idToSelect: String?)
  : TableEventListener, Disposable {

  /** If not possible to restore selection, or there is no selection we will select first row. */
  private var selectFirstRow = true

  /**
   * We are using two methods for restoring position on data update.
   * First and priority - entity id
   * Second, if no id found - line number
   */
  private var selectedId: String? = null
  private var selectionIndex = -1

  init {
    table.tableModel.addListener(this)
    selectedId = idToSelect

    if (!table.isEmpty) {
      TableRowAutoselector.performSelection(table, idToSelect)
    }
  }

  override fun dispose() {
    table.tableModel.removeListener(this)
  }

  // region TableEventListener
  override fun beforeChanged() {
    if (table.selectedRow != -1 && !table.isEmpty) {
      val row = table.getSelectedData()
      selectedId = if (row == null) null else table.tableModel.getDataModel()?.getId(row)
      selectionIndex = table.selectionModel.minSelectionIndex
    }
  }

  override fun onChanged() {
    if (table.isEmpty) {
      return
    }

    val selectedId = selectedId
    var index: Int? = when {
      selectedId != null -> {
        val modelIndex = table.tableModel.getIndexById(selectedId)
        modelIndex?.let { table.convertRowIndexToView(it) }
      }
      selectionIndex != -1 -> selectionIndex
      else -> null
    }


    if (index == null) {
      if (selectFirstRow)
        index = 0
      else
        return
    }

    if (index >= 0 && table.rowCount > index) {
      table.setRowSelectionInterval(index, index)
    }
  }
  //endregion TableEventListener

  companion object {
    fun <T : RemoteInfo> installOn(table: DataTable<T>, idToSelect: String? = null) {
      val tableSelectionPreserver = TableSelectionPreserver(table, idToSelect)
      Disposer.register(table, tableSelectionPreserver)
    }
  }
}