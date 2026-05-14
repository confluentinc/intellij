package io.confluent.intellijplugin.common.editor

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.thisLogger
import io.confluent.intellijplugin.consumer.data.CircularBuffer
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableColumnModel
import javax.swing.table.TableColumn
import javax.swing.table.TableColumnModel

/**
 * Table model backed by a [CircularBuffer]. JBTable's [AbstractTableModel] contract requires
 * contiguous row indices `0..rowCount-1`; the buffer's slot indices are not contiguous after wrap.
 * The translation `slot = (head + row) % capacity` is performed inside this class — callers see
 * row indices, the index layer (introduced in a later PR) sees slot indices, and they meet here.
 */
class ListTableModel<T : Any>(
    capacity: Int,
    private val columnNames: List<String>,
    private val onSlotChange: (slot: Int, prev: T?, next: T?) -> Unit = { _, _, _ -> },
    private val columnMapper: (T, Int) -> Any?,
) : AbstractTableModel() {

    private val logger = thisLogger()
    private val buffer = CircularBuffer<T>(capacity)

    val capacity: Int get() = buffer.capacity

    val columnModel: TableColumnModel by lazy {
        DefaultTableColumnModel().apply {
            columnNames.forEachIndexed { index, name ->
                addColumn(TableColumn(index).apply { headerValue = name })
            }
        }
    }

    var columnClasses: List<Class<*>>? = null

    /** Soft cap honored on flush; clamped to the buffer capacity. */
    var maxElementsCount: Int = capacity

    private val pendingAdds = mutableListOf<T>()
    private val flushScheduled = AtomicBoolean(false)
    private val clearListeners = mutableListOf<() -> Unit>()

    /** Translate a model row to its underlying buffer slot. Stable across repaints. */
    fun slotForRow(row: Int): Int = (buffer.head + row) % buffer.capacity

    override fun getRowCount(): Int = buffer.size
    override fun getColumnCount(): Int = columnNames.size

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? =
        try {
            val slot = slotForRow(rowIndex)
            val value = buffer.get(slot) ?: return null
            columnMapper(value, columnIndex)
        } catch (e: Exception) {
            logger.warn(e)
            null
        }

    override fun getColumnClass(columnIndex: Int): Class<*> =
        columnClasses?.get(columnIndex) ?: super.getColumnClass(columnIndex)

    fun getValueAt(rowIndex: Int): T? =
        try {
            buffer.get(slotForRow(rowIndex))
        } catch (e: Exception) {
            logger.warn(e)
            null
        }

    fun addClearListener(listener: () -> Unit) {
        clearListeners.add(listener)
    }

    fun clear() {
        resetAndClearData()
        fireTableDataChanged()
    }

    /**
     * Synchronously replace all data. Unlike [addBatch], this does not defer via `invokeLater`,
     * so callers can read [elements] immediately after this call returns. Used for restoring
     * from prior state.
     */
    fun replaceAll(elements: List<T>) {
        resetAndClearData()
        elements.forEach { applyAppend(it) }
        fireTableDataChanged()
    }

    private fun resetAndClearData() {
        synchronized(pendingAdds) { pendingAdds.clear() }
        flushScheduled.set(false)
        buffer.clear()
        clearListeners.forEach { it() }
    }

    /**
     * Add elements in a batch. Items are queued and flushed in a single EDT event via
     * `invokeLater`, so multiple `addBatch` calls between flushes are coalesced into one
     * table update.
     */
    fun addBatch(elements: List<T>) {
        if (elements.isEmpty()) return
        synchronized(pendingAdds) { pendingAdds.addAll(elements) }
        scheduleFlush()
    }

    private fun scheduleFlush() {
        if (flushScheduled.compareAndSet(false, true)) {
            invokeLater {
                try {
                    flushPendingAdds()
                } finally {
                    flushScheduled.set(false)
                    if (synchronized(pendingAdds) { pendingAdds.isNotEmpty() }) {
                        scheduleFlush()
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

        val effectiveCap = effectiveMax()
        if (toAdd.size > effectiveCap) {
            toAdd = toAdd.takeLast(effectiveCap)
        }

        val sizeBefore = buffer.size
        val totalAfterAdd = sizeBefore + toAdd.size
        val evictionCount = (totalAfterAdd - effectiveCap).coerceAtLeast(0)

        // Soft cap below buffer capacity: the buffer won't wrap on its own, so evict explicitly.
        // When effectiveCap == buffer.capacity, evictions surface inside applyAppend via wrap.
        if (evictionCount > 0 && effectiveCap < buffer.capacity) {
            repeat(evictionCount) {
                val evictedSlot = buffer.head
                val evicted = buffer.removeHead() ?: return@repeat
                onSlotChange(evictedSlot, evicted, null)
            }
        }

        if (evictionCount > 0) {
            fireTableRowsDeleted(0, evictionCount - 1)
        }

        val insertStart = buffer.size
        for (element in toAdd) {
            applyAppend(element)
        }
        val insertEnd = buffer.size - 1
        if (insertEnd >= insertStart) {
            fireTableRowsInserted(insertStart, insertEnd)
        }
    }

    /**
     * Append a single element to the buffer and notify the slot-change listener. Does not fire
     * table model events — callers are responsible for choosing the right event semantics.
     */
    private fun applyAppend(element: T) {
        val (slot, evicted) = buffer.append(element)
        onSlotChange(slot, evicted, element)
    }

    private fun effectiveMax(): Int =
        if (maxElementsCount > 0) minOf(maxElementsCount, buffer.capacity) else buffer.capacity

    /** Returns all elements including any that are pending flush, in insertion order. */
    fun elements(): List<T> {
        val flushed = buffer.toList()
        val pending = synchronized(pendingAdds) { pendingAdds.toList() }
        return flushed + pending
    }
}
