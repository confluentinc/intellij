package io.confluent.intellijplugin.consumer.data

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SearchBitSetBuilderTest {

    @Test
    fun `empty buffer yields empty BitSet`() {
        val buffer = CircularBuffer<String>(capacity = 4)
        val bits = buildSearchBitSet(buffer) { it.contains("x") }
        assertTrue(bits.isEmpty)
    }

    @Test
    fun `no matches yield empty BitSet`() {
        val buffer = CircularBuffer<String>(capacity = 4)
        listOf("alpha", "beta", "gamma").forEach { buffer.append(it) }
        val bits = buildSearchBitSet(buffer) { it.contains("zzz") }
        assertTrue(bits.isEmpty)
    }

    @Test
    fun `all matches set all live slot bits`() {
        val buffer = CircularBuffer<String>(capacity = 4)
        listOf("foo1", "foo2", "foo3").forEach { buffer.append(it) }
        val bits = buildSearchBitSet(buffer) { it.contains("foo") }
        assertEquals(setOf(0, 1, 2), bits.stream().toArray().toSet())
    }

    @Test
    fun `partial matches set only matching slot bits`() {
        val buffer = CircularBuffer<String>(capacity = 4)
        listOf("foo", "bar", "foobar", "baz").forEach { buffer.append(it) }
        val bits = buildSearchBitSet(buffer) { it.contains("foo") }
        assertEquals(setOf(0, 2), bits.stream().toArray().toSet())
    }

    @Test
    fun `bits use slot indices not insertion order after wrap`() {
        val buffer = CircularBuffer<String>(capacity = 3)
        // Fill, then evict; matching record ends up at slot 0 after wrap.
        listOf("alpha", "beta", "gamma", "foo").forEach { buffer.append(it) }
        // Live: slot 0=foo, slot 1=beta, slot 2=gamma. Head=1 (oldest=beta).

        val bits = buildSearchBitSet(buffer) { it.contains("foo") }

        // The match must be reported at slot 0 (the actual slot), not at "row 2".
        assertEquals(setOf(0), bits.stream().toArray().toSet())
    }
}
