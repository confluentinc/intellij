package io.confluent.intellijplugin.consumer.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.BitSet

class FreeTextSlotIndexTest {

    /** Case-insensitive substring matcher, mirroring the production free-text contains check. */
    private fun matcher(element: String, term: String): Boolean = element.contains(term, ignoreCase = true)

    /** Builds an index whose rescan source is the supplied mutable (slot -> element) map. */
    private fun indexOver(source: Map<Int, String>): FreeTextSlotIndex<String> =
        FreeTextSlotIndex(
            capacity = 8,
            matcher = ::matcher,
            slotElements = { source.entries.asSequence().map { it.key to it.value } },
        )

    private fun BitSet?.setBits(): Set<Int> = this?.stream()?.toArray()?.toSet() ?: emptySet()

    @Test
    fun `no active term yields null bitSet`() {
        val index = indexOver(emptyMap())
        assertNull(index.bitSet())
    }

    @Test
    fun `setTerm rescans current elements into slot-keyed bits`() {
        val index = indexOver(mapOf(0 to "alpha", 1 to "foobar", 2 to "gamma", 5 to "foo"))
        index.setTerm("foo")
        assertEquals(setOf(1, 5), index.bitSet().setBits())
    }

    @Test
    fun `blank term clears the index back to null`() {
        val index = indexOver(mapOf(0 to "foo"))
        index.setTerm("foo")
        assertEquals(setOf(0), index.bitSet().setBits())
        index.setTerm("")
        assertNull(index.bitSet())
    }

    @Test
    fun `onAppend sets the bit for a matching element when a term is active`() {
        val index = indexOver(emptyMap())
        index.setTerm("foo")
        index.onAppend(slot = 3, element = "foobar")
        assertEquals(setOf(3), index.bitSet().setBits())
    }

    @Test
    fun `onAppend clears a reused slot when the new element no longer matches`() {
        // Slot 3 matched under the old occupant; after wrap a non-matching record reuses it.
        val index = indexOver(mapOf(3 to "foobar"))
        index.setTerm("foo")
        assertEquals(setOf(3), index.bitSet().setBits())
        index.onAppend(slot = 3, element = "bar")
        assertTrue(index.bitSet().setBits().isEmpty())
    }

    @Test
    fun `onAppend is a no-op when no term is active`() {
        val index = indexOver(emptyMap())
        index.onAppend(slot = 1, element = "foo")
        assertNull(index.bitSet())
    }

    @Test
    fun `onEvict clears the freed slot bit`() {
        val index = indexOver(mapOf(2 to "foo", 4 to "foo"))
        index.setTerm("foo")
        assertEquals(setOf(2, 4), index.bitSet().setBits())
        index.onEvict(slot = 2)
        assertEquals(setOf(4), index.bitSet().setBits())
    }

    @Test
    fun `onClear empties the bits but keeps the term active for later appends`() {
        val index = indexOver(mapOf(0 to "foo"))
        index.setTerm("foo")
        index.onClear()
        assertTrue(index.bitSet().setBits().isEmpty())
        // Term still active: a fresh matching append after clear is reflected.
        index.onAppend(slot = 1, element = "foo")
        assertEquals(setOf(1), index.bitSet().setBits())
    }

    @Test
    fun `setTerm with the unchanged term keeps the live bitset instead of rescanning`() {
        val index = indexOver(mapOf(0 to "foo"))
        index.setTerm("foo")
        val first = index.bitSet()
        // A column-filter-only change re-invokes setTerm with the same term; it must not rebuild.
        index.setTerm("foo")
        assertSame(first, index.bitSet(), "Same term must reuse the incrementally-maintained bitset")
    }

    @Test
    fun `setTerm to a new term rescans and replaces prior bits`() {
        val index = indexOver(mapOf(0 to "alpha", 1 to "beta"))
        index.setTerm("alpha")
        assertEquals(setOf(0), index.bitSet().setBits())
        index.setTerm("beta")
        assertEquals(setOf(1), index.bitSet().setBits())
    }
}
