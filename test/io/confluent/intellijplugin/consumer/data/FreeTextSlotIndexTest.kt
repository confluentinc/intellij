package io.confluent.intellijplugin.consumer.data

import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.common.editor.ListTableModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.BitSet
import javax.swing.SwingUtilities

class FreeTextSlotIndexTest {

    /** Case-insensitive substring match. */
    private fun matcher(element: String, term: String): Boolean = element.contains(term, ignoreCase = true)

    /** Builds an index that rescans from [source] (slot -> element). */
    private fun indexOver(source: Map<Int, String>): FreeTextSlotIndex<String> =
        FreeTextSlotIndex(
            capacity = 8,
            matcher = ::matcher,
            slotElements = { source.entries.asSequence().map { it.key to it.value } },
        )

    private fun BitSet?.setBits(): Set<Int> = this?.stream()?.toArray()?.toSet() ?: emptySet()

    // The index's mutating methods assert EDT access.
    private fun onEdt(block: () -> Unit) = SwingUtilities.invokeAndWait(block)

    /** Block until queued EDT events (e.g. the model's flush) have run. */
    private fun drainEdt() = SwingUtilities.invokeAndWait { }

    @Test
    fun `no active term yields null bitSet`() {
        val index = indexOver(emptyMap())
        assertNull(index.bitSet())
    }

    @Test
    fun `setTerm rescans current elements into slot-keyed bits`() {
        val index = indexOver(mapOf(0 to "alpha", 1 to "foobar", 2 to "gamma", 5 to "foo"))
        onEdt { index.setTerm("foo") }
        assertEquals(setOf(1, 5), index.bitSet().setBits())
    }

    @Test
    fun `blank term clears the index back to null`() {
        val index = indexOver(mapOf(0 to "foo"))
        onEdt { index.setTerm("foo") }
        assertEquals(setOf(0), index.bitSet().setBits())
        onEdt { index.setTerm("") }
        assertNull(index.bitSet())
    }

    @Test
    fun `onAppend sets the bit for a matching element when a term is active`() {
        val index = indexOver(emptyMap())
        onEdt {
            index.setTerm("foo")
            index.onAppend(slot = 3, element = "foobar")
        }
        assertEquals(setOf(3), index.bitSet().setBits())
    }

    @Test
    fun `onAppend clears a reused slot when the new element no longer matches`() {
        // A non-matching record reuses slot 3, which matched under its previous occupant.
        val index = indexOver(mapOf(3 to "foobar"))
        onEdt { index.setTerm("foo") }
        assertEquals(setOf(3), index.bitSet().setBits())
        onEdt { index.onAppend(slot = 3, element = "bar") }
        assertTrue(index.bitSet().setBits().isEmpty())
    }

    @Test
    fun `onAppend is a no-op when no term is active`() {
        val index = indexOver(emptyMap())
        onEdt { index.onAppend(slot = 1, element = "foo") }
        assertNull(index.bitSet())
    }

    @Test
    fun `onEvict clears the freed slot bit`() {
        val index = indexOver(mapOf(2 to "foo", 4 to "foo"))
        onEdt { index.setTerm("foo") }
        assertEquals(setOf(2, 4), index.bitSet().setBits())
        onEdt { index.onEvict(slot = 2) }
        assertEquals(setOf(4), index.bitSet().setBits())
    }

    @Test
    fun `onClear empties the bits but keeps the term active for later appends`() {
        val index = indexOver(mapOf(0 to "foo"))
        onEdt {
            index.setTerm("foo")
            index.onClear()
        }
        assertTrue(index.bitSet().setBits().isEmpty())
        // The term is still active, so a matching append after clear is reflected.
        onEdt { index.onAppend(slot = 1, element = "foo") }
        assertEquals(setOf(1), index.bitSet().setBits())
    }

    @Test
    fun `setTerm with the unchanged term keeps the live bitset instead of rescanning`() {
        val index = indexOver(mapOf(0 to "foo"))
        onEdt { index.setTerm("foo") }
        val first = index.bitSet()
        // Same term again: must not rebuild.
        onEdt { index.setTerm("foo") }
        assertSame(first, index.bitSet(), "Same term must reuse the incrementally-maintained bitset")
    }

    @Test
    fun `setTerm to a new term rescans and replaces prior bits`() {
        val index = indexOver(mapOf(0 to "alpha", 1 to "beta"))
        onEdt { index.setTerm("alpha") }
        assertEquals(setOf(0), index.bitSet().setBits())
        onEdt { index.setTerm("beta") }
        assertEquals(setOf(1), index.bitSet().setBits())
    }

    @Nested
    @TestApplication
    inner class WrapIntegration {
        // Wires the index to a real ListTableModel, as KafkaRecordsOutput does.
        private fun wire(capacity: Int): Pair<ListTableModel<String>, FreeTextSlotIndex<String>> {
            lateinit var index: FreeTextSlotIndex<String>
            val model = ListTableModel(
                capacity = capacity,
                columnNames = listOf("c"),
                onSlotChange = { slot, next ->
                    if (next != null) index.onAppend(slot, next) else index.onEvict(slot)
                },
                columnMapper = { v: String, _: Int -> v },
            )
            index = FreeTextSlotIndex(
                capacity = capacity,
                matcher = ::matcher,
                slotElements = {
                    (0 until model.rowCount).asSequence()
                        .mapNotNull { row -> model.getValueAt(row)?.let { model.slotForRow(row) to it } }
                },
            )
            return model to index
        }

        @Test
        fun `rescan after a real buffer wrap reports the match at its live slot, not insertion order`() {
            val (model, index) = wire(capacity = 3)

            // Two batches: a single oversized batch is trimmed instead of wrapping.
            model.addBatch(listOf("beta", "gamma", "alpha"))
            drainEdt()
            model.addBatch(listOf("foo"))
            drainEdt()

            SwingUtilities.invokeAndWait { index.setTerm("foo") }

            // "foo" reuses the evicted head's slot (0), not its row position (2).
            assertEquals(setOf(0), index.bitSet().setBits())
        }

        @Test
        fun `live append through a wrap updates the correct slot without a rescan`() {
            val (model, index) = wire(capacity = 3)

            model.addBatch(listOf("beta", "gamma", "alpha"))
            drainEdt()
            SwingUtilities.invokeAndWait { index.setTerm("foo") }
            assertTrue(index.bitSet().setBits().isEmpty())

            // Appending "foo" evicts "beta" and reuses slot 0 via onAppend.
            model.addBatch(listOf("foo"))
            drainEdt()

            assertEquals(setOf(0), index.bitSet().setBits())
        }
    }
}
