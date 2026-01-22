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

    val columnModel: TableColumnModel by lazy {
        DefaultTableColumnModel().apply {
            columnNames.forEachIndexed { index, name ->
                addColumn(TableColumn(index).apply { headerValue = name })
            }
        }
    }

    var columnClasses: List<Class<*>>? = null

    /** If maxElementsCount >= data.size, the first element will be removed when adding a new element. */
    var maxElementsCount = 0

    override fun getRowCount() = data.size
    override fun getColumnCount() = columnNames.size
    override fun getValueAt(rowIndex: Int, columnIndex: Int) = columnMapper(data[rowIndex], columnIndex)
    override fun getColumnClass(columnIndex: Int): Class<*> {
        return columnClasses?.get(columnIndex) ?: super.getColumnClass(columnIndex)
    }

    fun getValueAt(rowIndex: Int): T? = if (rowIndex in data.indices) data[rowIndex] else null

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