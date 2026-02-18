package io.confluent.intellijplugin.common.editor

import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableColumnModel
import javax.swing.table.TableColumn
import javax.swing.table.TableColumnModel

class ListTableModel<T>(
    private val data: MutableList<T>,
    private val columnNames: List<String>,
    private val columnMapper: (T, Int) -> Any?
) : AbstractTableModel() {

    // Immutable snapshot used by Swing's read methods (getRowCount/getValueAt)
    // to ensure consistent data within a single rendering pass.
    private var snapshot: List<T> = ArrayList(data)

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

    override fun getRowCount() = snapshot.size
    override fun getColumnCount() = columnNames.size
    override fun getValueAt(rowIndex: Int, columnIndex: Int) = columnMapper(snapshot[rowIndex], columnIndex)
    override fun getColumnClass(columnIndex: Int): Class<*> {
        return columnClasses?.get(columnIndex) ?: super.getColumnClass(columnIndex)
    }

    fun getValueAt(rowIndex: Int): T? = if (rowIndex in snapshot.indices) snapshot[rowIndex] else null

    fun clear() {
        data.clear()
        snapshot = emptyList()
        fireTableDataChanged()
    }

    fun addElement(element: T) {
        if (maxElementsCount > 0 && data.size >= maxElementsCount) {
            val elementsToRemove = data.size - maxElementsCount + 1
            for (i in 0 until elementsToRemove) {
                data.removeFirst()
            }
            snapshot = ArrayList(data)
            fireTableRowsDeleted(0, elementsToRemove - 1)
        }
        data += element
        snapshot = ArrayList(data)
        fireTableRowsInserted(data.size - 1, data.size - 1)
    }

    fun elements(): List<T> = snapshot
}