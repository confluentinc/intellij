package io.confluent.intellijplugin.common.editor

import com.intellij.openapi.diagnostic.thisLogger
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableColumnModel
import javax.swing.table.TableColumn
import javax.swing.table.TableColumnModel

class ListTableModel<T>(
    private val data: MutableList<T>,
    private val columnNames: List<String>,
    private val columnMapper: (T, Int) -> Any?
) : AbstractTableModel() {

    val columnModel: TableColumnModel by lazy {
        DefaultTableColumnModel().apply {
            columnNames.forEachIndexed { index, name ->
                addColumn(TableColumn(index).apply { headerValue = name })
            }
        }
    }

    var columnClasses: List<Class<*>>? = null

    /** If maxElementsCount <= data.size, the first element will be removed when adding a new element. */
    var maxElementsCount = 0

    override fun getRowCount() = data.size
    override fun getColumnCount() = columnNames.size
    // Guard against stale indices from concurrent list modification.
    // TODO: address with MessageViewer upgrade
    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? =
        try {
            columnMapper(data[rowIndex], columnIndex)
        } catch (e: Exception) {
            thisLogger().warn(e)
            null
        }
    override fun getColumnClass(columnIndex: Int): Class<*> {
        return columnClasses?.get(columnIndex) ?: super.getColumnClass(columnIndex)
    }

    fun getValueAt(rowIndex: Int): T? =
        try { data[rowIndex] } catch (_: Exception) { null }

    fun clear() {
        data.clear()
        fireTableDataChanged()
    }

    fun addElement(element: T) {
        if (maxElementsCount > 0 && data.size >= maxElementsCount) {
            val elementsToRemove = data.size - maxElementsCount + 1
            for (i in 0 until elementsToRemove) {
                data.removeFirst()
            }
            fireTableRowsDeleted(0, elementsToRemove - 1)
        }
        data += element
        fireTableRowsInserted(data.size - 1, data.size - 1)
    }

    fun elements(): List<T> = data
}