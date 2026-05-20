package io.confluent.intellijplugin.common.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import javax.swing.event.TableModelEvent
import javax.swing.event.TableModelListener

@TestApplication
class ListTableModelTest {

    private lateinit var model: ListTableModel<String>
    private val events = mutableListOf<TableModelEvent>()

    @BeforeEach
    fun setup() {
        model = ListTableModel<String>(
            capacity = 100,
            columnNames = listOf("Column1", "Column2"),
        ) { data, index ->
            when (index) {
                0 -> data
                1 -> data.length
                else -> ""
            }
        }
        events.clear()
        model.addTableModelListener(object : TableModelListener {
            override fun tableChanged(e: TableModelEvent) {
                events.add(e)
            }
        })
    }

    @Nested
    inner class BatchedUpdates {

        @Test
        fun `should fire single event for batch of elements`() {
            // Given
            val batch = listOf("item1", "item2", "item3", "item4", "item5")

            // When
            model.addBatch(batch)
            ApplicationManager.getApplication().invokeAndWait { }  // Wait for EDT flush

            // Then
            assertEquals(1, events.size, "Expected single table event for batch")
            assertEquals(TableModelEvent.INSERT, events[0].type)
            assertEquals(0, events[0].firstRow)
            assertEquals(4, events[0].lastRow)
            assertEquals(5, model.rowCount)
        }

        @Test
        fun `should accumulate multiple addBatch calls into single event`() {
            // Given
            val batch1 = listOf("a", "b")
            val batch2 = listOf("c", "d")
            val batch3 = listOf("e")

            // When - call multiple times before EDT flush
            model.addBatch(batch1)
            model.addBatch(batch2)
            model.addBatch(batch3)
            ApplicationManager.getApplication().invokeAndWait { }  // Wait for EDT flush

            // Then
            assertEquals(1, events.size, "Expected single event for accumulated batches")
            assertEquals(5, model.rowCount)
            assertEquals(0, events[0].firstRow)
            assertEquals(4, events[0].lastRow)
        }

        @Test
        fun `should handle empty batch gracefully`() {
            // When
            model.addBatch(emptyList())
            ApplicationManager.getApplication().invokeAndWait { }

            // Then
            assertEquals(0, events.size, "No event should be fired for empty batch")
            assertEquals(0, model.rowCount)
        }
    }

    @Nested
    inner class CapacityManagement {

        @Test
        fun `should enforce capacity limit with batching`() {
            // Given
            model.maxElementsCount = 10
            val initialBatch = (1..8).map { "item$it" }
            model.addBatch(initialBatch)
            ApplicationManager.getApplication().invokeAndWait { }
            events.clear()

            // When - add batch that exceeds capacity
            val newBatch = listOf("new1", "new2", "new3", "new4", "new5")
            model.addBatch(newBatch)
            ApplicationManager.getApplication().invokeAndWait { }

            // Then
            assertEquals(10, model.rowCount, "Should not exceed max capacity")
            assertEquals(2, events.size, "Should have delete event + insert event")

            // First event should be deletion (evict before adding)
            assertEquals(TableModelEvent.DELETE, events[0].type)
            assertEquals(0, events[0].firstRow)
            assertEquals(2, events[0].lastRow)  // Removed 3 elements

            // Second event should be insertion with correct indices
            assertEquals(TableModelEvent.INSERT, events[1].type)
            assertEquals(5, events[1].firstRow)
            assertEquals(9, events[1].lastRow)

            // Verify oldest elements were removed
            assertEquals("item4", model.getValueAt(0))
            assertEquals("new5", model.getValueAt(9))
        }

        @Test
        fun `should handle batch larger than max capacity`() {
            // Given
            model.maxElementsCount = 5
            val largeBatch = (1..10).map { "item$it" }

            // When
            model.addBatch(largeBatch)
            ApplicationManager.getApplication().invokeAndWait { }

            // Then
            assertEquals(5, model.rowCount, "Should keep only max allowed")
            assertEquals("item6", model.getValueAt(0), "Should keep latest items")
            assertEquals("item10", model.getValueAt(4))
        }
    }

    @Nested
    inner class DataAccess {

        @Test
        fun `should support random access across large dataset`() {
            // Given - large dataset that fits within the buffer capacity
            val large = ListTableModel<String>(capacity = 1000, columnNames = listOf("c")) { v, _ -> v }
            val largeBatch = (0..999).map { "item$it" }
            large.addBatch(largeBatch)
            ApplicationManager.getApplication().invokeAndWait { }

            assertEquals(1000, large.rowCount)
            assertEquals("item0", large.getValueAt(0, 0))
            assertEquals("item499", large.getValueAt(499, 0))
            assertEquals("item999", large.getValueAt(999, 0))
        }

        @Test
        fun `elements should include pending unflushed items`() {
            // Given - some flushed data
            model.addBatch(listOf("flushed1", "flushed2"))
            ApplicationManager.getApplication().invokeAndWait { }

            // When - add more without flushing
            model.addBatch(listOf("pending1", "pending2"))

            // Then - elements() includes both flushed and pending
            val elements = model.elements()
            assertEquals(listOf("flushed1", "flushed2", "pending1", "pending2"), elements)
        }

        @Test
        fun `getValueAt by row index should return correct element`() {
            // Given
            model.addBatch(listOf("first", "second", "third"))
            ApplicationManager.getApplication().invokeAndWait { }

            // When/Then
            assertEquals("first", model.getValueAt(0))
            assertEquals("second", model.getValueAt(1))
            assertEquals("third", model.getValueAt(2))
            assertNull(model.getValueAt(3))
            assertNull(model.getValueAt(-1))
        }
    }

    @Nested
    inner class ReplaceAllOperation {

        @Test
        fun `replaceAll should make data available immediately without EDT flush`() {
            // When
            model.replaceAll(listOf("a", "b", "c"))

            // Then - data is available synchronously, no invokeAndWait needed
            assertEquals(3, model.rowCount)
            assertEquals("a", model.getValueAt(0))
            assertEquals("b", model.getValueAt(1))
            assertEquals("c", model.getValueAt(2))
            assertEquals(listOf("a", "b", "c"), model.elements())
        }

        @Test
        fun `replaceAll should discard unflushed pending adds`() {
            // Given - add batch but don't flush yet
            model.addBatch(listOf("pending1", "pending2"))

            // When - replaceAll before EDT flush
            model.replaceAll(listOf("replaced"))
            ApplicationManager.getApplication().invokeAndWait { }

            // Then - only the replaced data should exist
            assertEquals(1, model.rowCount)
            assertEquals("replaced", model.getValueAt(0))
        }

        @Test
        fun `replaceAll should clear existing data before adding new elements`() {
            // Given
            model.addBatch(listOf("old1", "old2"))
            ApplicationManager.getApplication().invokeAndWait { }

            // When
            model.replaceAll(listOf("new1"))

            // Then
            assertEquals(1, model.rowCount)
            assertEquals("new1", model.getValueAt(0))
        }
    }

    @Nested
    inner class ClearOperation {

        @Test
        fun `clear should remove all elements and fire event`() {
            // Given
            model.addBatch(listOf("a", "b", "c"))
            ApplicationManager.getApplication().invokeAndWait { }
            events.clear()

            // When
            model.clear()

            // Then
            assertEquals(0, model.rowCount)
            assertEquals(1, events.size)
            assertEquals(TableModelEvent.UPDATE, events[0].type)
        }

        @Test
        fun `clear should reset batching state so new adds flush correctly`() {
            // Given - simulate the bug scenario: add data, clear, then add more
            model.addBatch(listOf("old1", "old2"))
            ApplicationManager.getApplication().invokeAndWait { }
            events.clear()

            // When - clear and then add new data
            model.clear()
            model.addBatch(listOf("new1", "new2"))
            ApplicationManager.getApplication().invokeAndWait { }

            // Then - new data should appear
            assertEquals(2, model.rowCount, "New data should be flushed after clear")
            assertEquals("new1", model.getValueAt(0))
            assertEquals("new2", model.getValueAt(1))
        }

        @Test
        fun `clear should discard unflushed pending adds`() {
            // Given - add batch but don't flush yet
            model.addBatch(listOf("pending1", "pending2"))

            // When - clear before EDT flush
            model.clear()
            ApplicationManager.getApplication().invokeAndWait { }

            // Then - pending data should be discarded
            assertEquals(0, model.rowCount, "Pending adds should be discarded on clear")
        }

        @Test
        fun `clear should fire registered clear listeners`() {
            // Given
            var callCount = 0
            model.addClearListener { callCount++ }
            model.addBatch(listOf("a"))
            ApplicationManager.getApplication().invokeAndWait { }

            // When
            model.clear()

            // Then
            assertEquals(1, callCount)
        }

        @Test
        fun `replaceAll should also fire clear listeners so external indices reset`() {
            var callCount = 0
            model.addClearListener { callCount++ }
            model.addBatch(listOf("a"))
            ApplicationManager.getApplication().invokeAndWait { }

            model.replaceAll(listOf("x", "y"))

            assertEquals(1, callCount)
        }
    }

    @Nested
    inner class CircularBufferWrap {

        @Test
        fun `getValueAt(0) should return oldest live element after wrap`() {
            // Given a small-capacity model
            val small = ListTableModel<String>(capacity = 3, columnNames = listOf("c")) { v, _ -> v }
            // A single oversized batch is trimmed to the last `effectiveCap` elements before
            // appending; wrap is exercised by the multi-batch test below.
            small.addBatch(listOf("a", "b", "c", "d", "e"))
            ApplicationManager.getApplication().invokeAndWait { }

            // Then
            assertEquals(3, small.rowCount)
            assertEquals("c", small.getValueAt(0))
            assertEquals("d", small.getValueAt(1))
            assertEquals("e", small.getValueAt(2))
        }

        @Test
        fun `elements should preserve oldest-to-newest after wrap`() {
            val small = ListTableModel<String>(capacity = 3, columnNames = listOf("c")) { v, _ -> v }
            small.addBatch(listOf("a", "b", "c", "d", "e"))
            ApplicationManager.getApplication().invokeAndWait { }

            assertEquals(listOf("c", "d", "e"), small.elements())
        }

        @Test
        fun `slotForRow should reflect buffer wrap`() {
            val small = ListTableModel<String>(capacity = 3, columnNames = listOf("c")) { v, _ -> v }
            // Two batches so the buffer actually wraps (a single oversized batch is trimmed).
            small.addBatch(listOf("a", "b", "c"))
            ApplicationManager.getApplication().invokeAndWait { }
            small.addBatch(listOf("d"))
            ApplicationManager.getApplication().invokeAndWait { }

            // Live rows: row 0 → slot 1 (b), row 1 → slot 2 (c), row 2 → slot 0 (d)
            assertEquals(1, small.slotForRow(0))
            assertEquals(2, small.slotForRow(1))
            assertEquals(0, small.slotForRow(2))
            assertEquals("b", small.getValueAt(0))
            assertEquals("d", small.getValueAt(2))
        }
    }

    @Nested
    inner class SlotChangeCallback {

        @Test
        fun `should fire append event for each insert including wrap`() {
            data class Event(val slot: Int, val next: String?)
            val events = mutableListOf<Event>()
            val small = ListTableModel<String>(
                capacity = 3,
                columnNames = listOf("c"),
                onSlotChange = { slot, next -> events.add(Event(slot, next)) },
            ) { v, _ -> v }

            small.addBatch(listOf("a", "b", "c"))
            ApplicationManager.getApplication().invokeAndWait { }
            small.addBatch(listOf("d"))
            ApplicationManager.getApplication().invokeAndWait { }

            // Wrap reuses slot 0 for "d"; consumers discover the prior occupant from their own
            // slot→keys mapping (see ConsumerRecordIndex).
            assertEquals(
                listOf(Event(0, "a"), Event(1, "b"), Event(2, "c"), Event(0, "d")),
                events,
            )
        }

        @Test
        fun `should fire pure-eviction event when soft cap is below buffer capacity`() {
            data class Event(val slot: Int, val next: String?)
            val events = mutableListOf<Event>()
            val model = ListTableModel<String>(
                capacity = 100,
                columnNames = listOf("c"),
                onSlotChange = { slot, next -> events.add(Event(slot, next)) },
            ) { v, _ -> v }
            model.maxElementsCount = 3

            model.addBatch(listOf("a", "b", "c"))
            ApplicationManager.getApplication().invokeAndWait { }
            events.clear()

            model.addBatch(listOf("d"))
            ApplicationManager.getApplication().invokeAndWait { }

            // Soft cap evicts slot 0 without reusing it — buffer cap (100) is well above 4.
            assertEquals(
                listOf(
                    Event(0, null), // pure eviction, slot becomes empty
                    Event(3, "d"),  // append at next slot
                ),
                events,
            )
        }
    }

    @Nested
    inner class TableEventSemantics {

        @Test
        fun `wrap fires single fireTableDataChanged and preserves rowCount`() {
            val small = ListTableModel<String>(capacity = 3, columnNames = listOf("c")) { v, _ -> v }
            val collected = mutableListOf<TableModelEvent>()
            small.addTableModelListener { collected.add(it) }

            small.addBatch(listOf("a", "b", "c"))
            ApplicationManager.getApplication().invokeAndWait { }
            // Initial insert: rowCount 0 → 3.
            assertEquals(1, collected.size)
            assertEquals(TableModelEvent.INSERT, collected[0].type)
            collected.clear()

            small.addBatch(listOf("d"))
            ApplicationManager.getApplication().invokeAndWait { }

            // Wrap: every row index shifts, so a single fireTableDataChanged is fired —
            // emitting a delete event without an offsetting insert would leave listeners
            // inconsistent with rowCount (which stays at capacity).
            assertEquals(1, collected.size, "wrap should emit one event, got $collected")
            val event = collected[0]
            assertEquals(0, event.firstRow)
            assertEquals(Int.MAX_VALUE, event.lastRow)
            assertEquals(TableModelEvent.UPDATE, event.type)
            assertEquals(3, small.rowCount)
        }

        @Test
        fun `soft cap below buffer capacity fires delete then insert`() {
            val model = ListTableModel<String>(capacity = 100, columnNames = listOf("c")) { v, _ -> v }
            model.maxElementsCount = 3
            val collected = mutableListOf<TableModelEvent>()

            model.addBatch(listOf("a", "b", "c"))
            ApplicationManager.getApplication().invokeAndWait { }
            model.addTableModelListener { collected.add(it) }

            model.addBatch(listOf("d"))
            ApplicationManager.getApplication().invokeAndWait { }

            // One DELETE (row 0) and one INSERT (new last row), each fired against a
            // consistent rowCount: delete while size==3, insert after size==2 then →3.
            assertEquals(2, collected.size, "soft-cap eviction should emit delete + insert, got $collected")
            assertEquals(TableModelEvent.DELETE, collected[0].type)
            assertEquals(0, collected[0].firstRow)
            assertEquals(0, collected[0].lastRow)
            assertEquals(TableModelEvent.INSERT, collected[1].type)
            assertEquals(2, collected[1].firstRow)
            assertEquals(2, collected[1].lastRow)
            assertEquals(3, model.rowCount)
        }
    }
}
