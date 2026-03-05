package io.confluent.intellijplugin.common.editor

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.thisLogger
import java.util.concurrent.atomic.AtomicBoolean
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
    private val flushScheduled = AtomicBoolean(false)

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
        synchronized(pendingAdds) {
            pendingAdds.clear()
        }
        flushScheduled.set(false)
        data.clear()
        fireTableDataChanged()
    }

    /**
     * Synchronously replace all data. Unlike addBatch(), this does not defer via invokeLater,
     * so callers can read elements() immediately after this call returns.
     */
    fun replaceAll(elements: List<T>) {
        synchronized(pendingAdds) {
            pendingAdds.clear()
        }
        flushScheduled.set(false)
        data.clear()
        data.addAll(elements)
        fireTableDataChanged()
    }

    /**
     * Add multiple elements in a single batch. This is much more efficient than calling
     * addElement() multiple times as it fires only one table event instead of one per element.
     */
    fun addBatch(elements: List<T>) {
        if (elements.isEmpty()) return
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
        if (flushScheduled.compareAndSet(false, true)) {
            invokeLater {
                try {
                    flushPendingAdds()
                } finally {
                    flushScheduled.set(false)
                    // Re-check: items may have been added while we were flushing
                    synchronized(pendingAdds) { pendingAdds.isNotEmpty() }.let { hasMore ->
                        if (hasMore) scheduleFlush()
                    }
                }
            }
        }
    }

    private fun flushPendingAdds() {
        var toAdd = synchronized(pendingAdds) {
            val batch = pendingAdds.toList()
            pendingAdds.clear()
            batch
        }
        if (toAdd.isEmpty()) return

        // If batch alone exceeds capacity, keep only the latest items
        if (maxElementsCount > 0 && toAdd.size > maxElementsCount) {
            toAdd = toAdd.takeLast(maxElementsCount)
        }

        // Evict oldest existing elements to make room for the batch
        if (maxElementsCount > 0) {
            val totalAfterAdd = data.size + toAdd.size
            if (totalAfterAdd > maxElementsCount) {
                val elementsToRemove = totalAfterAdd - maxElementsCount
                repeat(elementsToRemove) { data.removeFirst() }
                fireTableRowsDeleted(0, elementsToRemove - 1)
            }
        }

        // Add elements after eviction so startIndex is correct
        val startIndex = data.size
        data.addAll(toAdd)
        fireTableRowsInserted(startIndex, data.size - 1)
    }

    fun elements(): List<T> = data
}