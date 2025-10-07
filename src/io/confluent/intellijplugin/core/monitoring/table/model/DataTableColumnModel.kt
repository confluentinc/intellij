package io.confluent.intellijplugin.core.monitoring.table.model

import com.intellij.openapi.Disposable
import io.confluent.intellijplugin.core.monitoring.data.model.RemoteInfo
import io.confluent.intellijplugin.core.monitoring.table.extension.LocalizedField
import io.confluent.intellijplugin.core.settings.ColumnVisibilitySettings
import javax.swing.event.ChangeEvent
import javax.swing.event.ListSelectionEvent
import javax.swing.event.TableColumnModelEvent
import javax.swing.event.TableColumnModelListener
import javax.swing.table.DefaultTableColumnModel
import javax.swing.table.TableColumn
import javax.swing.table.TableColumnModel

class DataTableColumnModel<T : RemoteInfo>(
    renderableColumns: List<LocalizedField<T>>,
    val columnSettings: ColumnVisibilitySettings
) : DefaultTableColumnModel(), Disposable {

    val allColumns: List<LocalizedField<T>>

    init {
        allColumns = renderableColumns

        columnSettings.visibleColumns.forEach { column ->
            allColumns.find { it.name == column }?.let { addInternalColumn(it) }
        }
        columnSettings.onColumnVisibilityChanged += ::onColumnVisibilityChanged

        addColumnModelListener(getModelListener())
    }

    private fun getModelListener() = object : TableColumnModelListener {
        override fun columnAdded(event: TableColumnModelEvent?) {}
        override fun columnRemoved(event: TableColumnModelEvent?) {}
        override fun columnMarginChanged(event: ChangeEvent?) {}
        override fun columnSelectionChanged(event: ListSelectionEvent?) {}

        override fun columnMoved(event: TableColumnModelEvent?) {
            event?.let { it ->
                val fromIndex = it.fromIndex
                val toIndex = it.toIndex

                if (fromIndex != toIndex) {
                    val columnModel = it.source as? TableColumnModel ?: return

                    columnSettings.visibleColumns.clear()
                    columnSettings.visibleColumns.addAll(columnModel.columns.toList().map { it.identifier.toString() })
                }
            }
        }
    }

    fun getModelIndex(column: String): Int {
        return allColumns.withIndex().find { it.value.name == column }!!.index
    }

    /** Could return null if column is invisible now. */
    fun getModelColumn(modelIndex: Int): TableColumn? {
        return tableColumns.find { it.modelIndex == modelIndex }
    }

    private fun addInternalColumn(column: LocalizedField<T>): TableColumn {
        val col = TableColumn(getModelIndex(column.name))
        col.identifier = column.name
        col.headerValue = column.getLocalizedName()
        addColumn(col)
        return col
    }

    private fun onColumnVisibilityChanged(column: String, visible: Boolean) {
        if (visible) {
            allColumns.find { it.name == column }?.let { addInternalColumn(it) }
        } else {
            val found = tableColumns.find { it.identifier as String == column }
            if (found != null) {
                removeColumn(found)
            }
        }
    }

    fun getColumnName(columnIndex: Int): String {
        return tableColumns[columnIndex].headerValue as String
    }

    override fun dispose() {
        columnSettings.onColumnVisibilityChanged -= ::onColumnVisibilityChanged
    }
}
