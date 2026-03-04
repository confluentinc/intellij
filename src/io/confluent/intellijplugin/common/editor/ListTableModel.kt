package io.confluent.intellijplugin.common.editor

import com.intellij.openapi.application.invokeLater
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

    private val logger = thisLogger()

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

    // Batching support for performance
    private val pendingAdds = mutableListOf<T>()
    private var flushScheduled = false

    override fun getRowCount() = data.size
    override fun getColumnCount() = columnNames.size
    // Guard against stale indices from concurrent list modification.
    // TODO: address with MessageViewer upgrade
    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? =
        try {
            columnMapper(data[rowIndex], columnIndex)
        } catch (e: Exception) {
            logger.warn(e)
            null
        }
    override fun getColumnClass(columnIndex: Int): Class<*> {
        return columnClasses?.get(columnIndex) ?: super.getColumnClass(columnIndex)
    }

    fun getValueAt(rowIndex: Int): T? =
        try {
            data[rowIndex]
        } catch (e: Exception) {
            logger.warn(e)
            null
        }

    fun clear() {
        data.clear()
        fireTableDataChanged()
    }

    /**
     * Add multiple elements in a single batch. This is much more efficient than calling
     * addElement() multiple times as it fires only one table event instead of one per element.
     */
    fun addBatch(elements: List<T>) {
        synchronized(pendingAdds) {
            pendingAdds.addAll(elements)
        }
        scheduleFlush()
    }

    /**
     * Add a single element. For backward compatibility.
     * For better performance when adding multiple elements, use addBatch() instead.
     */
    fun addElement(element: T) = addBatch(listOf(element))

    private fun scheduleFlush() {
        if (!flushScheduled) {
            flushScheduled = true
            invokeLater {
                flushPendingAdds()
                flushScheduled = false
            }
        }
    }

    private fun flushPendingAdds() {
        val toAdd = synchronized(pendingAdds) {
            val batch = pendingAdds.toList()
            pendingAdds.clear()
            batch
        }
        if (toAdd.isEmpty()) return

        // Add elements first
        val startIndex = data.size
        data.addAll(toAdd)

        // Then handle capacity limit if needed
        if (maxElementsCount > 0 && data.size > maxElementsCount) {
            val elementsToRemove = data.size - maxElementsCount
            repeat(elementsToRemove) { data.removeAt(0) }
            fireTableRowsDeleted(0, elementsToRemove - 1)
        }

        // Fire insert event for the added range
        fireTableRowsInserted(startIndex, data.size - 1)  // Single event for entire batch!
    }

    fun elements(): List<T> = data
}