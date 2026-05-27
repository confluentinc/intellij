package io.confluent.intellijplugin.consumer.data

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.BitSet

class ConsumerRecordIndexTest {

    private fun bitsOf(vararg slots: Int): BitSet =
        BitSet().also { bs -> slots.forEach { bs.set(it) } }

    private fun BitSet.toSlotList(): List<Int> = stream().toArray().toList()

    @Nested
    inner class Append {

        @Test
        fun `should index slot by timestamp and partition below capacity`() {
            val index = ConsumerRecordIndex(capacity = 4)

            index.onAppend(slot = 0, timestamp = 100L, partition = 0)
            index.onAppend(slot = 1, timestamp = 200L, partition = 1)
            index.onAppend(slot = 2, timestamp = 200L, partition = 0)

            assertEquals(3, index.size)
            assertEquals(listOf(0, 1, 2), index.timestampRangeBitSet(0L, 999L).toSlotList())
            assertEquals(listOf(0, 2), index.partitionBitSet(setOf(0)).toSlotList())
            assertEquals(listOf(1), index.partitionBitSet(setOf(1)).toSlotList())
        }

        @Test
        fun `should remove prior keys before reinserting at same slot on wrap`() {
            val index = ConsumerRecordIndex(capacity = 3)

            index.onAppend(0, 100L, 0)
            index.onAppend(1, 200L, 1)
            index.onAppend(2, 300L, 0)
            // Wrap: slot 0 was timestamp=100, partition=0. The index looks up the prior keys
            // internally — callers do not report them.
            index.onAppend(0, 400L, 2)

            assertEquals(3, index.size)
            assertEquals(emptyList<Int>(), index.timestampRangeBitSet(50L, 150L).toSlotList())
            assertEquals(listOf(0), index.timestampRangeBitSet(350L, 450L).toSlotList())
            assertEquals(listOf(2), index.partitionBitSet(setOf(0)).toSlotList())
            assertEquals(listOf(0), index.partitionBitSet(setOf(2)).toSlotList())
        }

        @Test
        fun `multiple records with same timestamp should coexist`() {
            val index = ConsumerRecordIndex(capacity = 4)

            index.onAppend(0, 500L, 0)
            index.onAppend(1, 500L, 0)
            index.onAppend(2, 500L, 1)

            assertEquals(listOf(0, 1, 2), index.timestampRangeBitSet(500L, 500L).toSlotList())
        }

        @Test
        fun `evicting one of several records sharing a timestamp should keep the rest`() {
            val index = ConsumerRecordIndex(capacity = 3)

            index.onAppend(0, 500L, 0)
            index.onAppend(1, 500L, 0)
            index.onAppend(2, 500L, 1)
            index.onAppend(0, 600L, 0)

            assertEquals(listOf(1, 2), index.timestampRangeBitSet(500L, 500L).toSlotList())
            assertEquals(listOf(0), index.timestampRangeBitSet(600L, 600L).toSlotList())
        }
    }

    @Nested
    inner class TimestampRange {

        @Test
        fun `should include both endpoints inclusively`() {
            val index = ConsumerRecordIndex(capacity = 4)
            index.onAppend(0, 100L, 0)
            index.onAppend(1, 200L, 0)
            index.onAppend(2, 300L, 0)

            assertEquals(listOf(1), index.timestampRangeBitSet(200L, 200L).toSlotList())
            assertEquals(listOf(0, 1, 2), index.timestampRangeBitSet(100L, 300L).toSlotList())
        }

        @Test
        fun `should return empty BitSet for inverted range`() {
            val index = ConsumerRecordIndex(capacity = 2)
            index.onAppend(0, 100L, 0)

            assertTrue(index.timestampRangeBitSet(200L, 100L).isEmpty)
        }
    }

    @Nested
    inner class Slice {

        @Test
        fun `should return newest-first slots respecting offset and limit`() {
            val index = ConsumerRecordIndex(capacity = 5)
            index.onAppend(0, 100L, 0)
            index.onAppend(1, 200L, 0)
            index.onAppend(2, 300L, 0)
            index.onAppend(3, 400L, 0)
            index.onAppend(4, 500L, 0)

            assertArrayEquals(intArrayOf(4, 3), index.slice(offset = 0, limit = 2, includes = null))
            assertArrayEquals(intArrayOf(2, 1), index.slice(offset = 2, limit = 2, includes = null))
        }

        @Test
        fun `should skip slots not in the includes BitSet for both offset and limit accounting`() {
            val index = ConsumerRecordIndex(capacity = 5)
            // timestamps newest-first: 4, 3, 2, 1, 0 → slots 4, 3, 2, 1, 0
            index.onAppend(0, 100L, 0)
            index.onAppend(1, 200L, 0)
            index.onAppend(2, 300L, 0)
            index.onAppend(3, 400L, 0)
            index.onAppend(4, 500L, 0)

            // Only slots 1, 3 match the predicate
            val includes = bitsOf(1, 3)

            // Offset 0: first match newest-first is slot 3
            assertArrayEquals(intArrayOf(3), index.slice(0, 1, includes))
            // Offset 1: skip slot 3, return slot 1
            assertArrayEquals(intArrayOf(1), index.slice(1, 1, includes))
            // limit larger than matches
            assertArrayEquals(intArrayOf(3, 1), index.slice(0, 99, includes))
        }

        @Test
        fun `empty index should return empty slice`() {
            val index = ConsumerRecordIndex(capacity = 3)
            assertArrayEquals(intArrayOf(), index.slice(0, 10, null))
        }

        @Test
        fun `limit zero should return empty regardless of contents`() {
            val index = ConsumerRecordIndex(capacity = 3)
            index.onAppend(0, 100L, 0)
            assertArrayEquals(intArrayOf(), index.slice(0, 0, null))
        }
    }

    @Nested
    inner class Walk {

        @Test
        fun `should yield slots newest-first with timestamp`() {
            val index = ConsumerRecordIndex(capacity = 3)
            index.onAppend(0, 100L, 0)
            index.onAppend(1, 300L, 0)
            index.onAppend(2, 200L, 0)

            val collected = mutableListOf<Pair<Int, Long>>()
            index.walkTimestampDescending(includes = null) { slot, ts ->
                collected.add(slot to ts); true
            }
            assertEquals(listOf(1 to 300L, 2 to 200L, 0 to 100L), collected)
        }

        @Test
        fun `should stop early when action returns false`() {
            val index = ConsumerRecordIndex(capacity = 3)
            index.onAppend(0, 100L, 0)
            index.onAppend(1, 200L, 0)
            index.onAppend(2, 300L, 0)

            val collected = mutableListOf<Int>()
            index.walkTimestampDescending(null) { slot, _ ->
                collected.add(slot); collected.size < 2
            }
            assertEquals(listOf(2, 1), collected)
        }
    }

    @Nested
    inner class Evict {

        @Test
        fun `should remove slot from both indices and decrement size`() {
            val index = ConsumerRecordIndex(capacity = 3)
            index.onAppend(0, 100L, 0)
            index.onAppend(1, 200L, 1)

            index.onEvict(slot = 0)

            assertEquals(1, index.size)
            assertTrue(index.timestampRangeBitSet(50L, 150L).isEmpty)
            assertTrue(index.partitionBitSet(setOf(0)).isEmpty)
            assertEquals(listOf(1), index.timestampRangeBitSet(150L, 250L).toSlotList())
            assertEquals(listOf(1), index.partitionBitSet(setOf(1)).toSlotList())
        }

        @Test
        fun `should leave sibling entries with same timestamp intact`() {
            val index = ConsumerRecordIndex(capacity = 3)
            index.onAppend(0, 500L, 0)
            index.onAppend(1, 500L, 1)

            index.onEvict(slot = 0)

            assertEquals(listOf(1), index.timestampRangeBitSet(500L, 500L).toSlotList())
        }

        @Test
        fun `evicting an empty slot is a no-op`() {
            val index = ConsumerRecordIndex(capacity = 3)
            index.onAppend(0, 100L, 0)

            index.onEvict(slot = 2)
            index.onEvict(slot = 2) // idempotent

            assertEquals(1, index.size)
            assertEquals(listOf(0), index.timestampRangeBitSet(0L, Long.MAX_VALUE).toSlotList())
        }
    }

    @Nested
    inner class Clear {

        @Test
        fun `should empty both indices and reset size`() {
            val index = ConsumerRecordIndex(capacity = 3)
            index.onAppend(0, 100L, 0)
            index.onAppend(1, 200L, 1)

            index.onClear()

            assertEquals(0, index.size)
            assertTrue(index.timestampRangeBitSet(0L, Long.MAX_VALUE).isEmpty)
            assertTrue(index.partitionBitSet(setOf(0, 1)).isEmpty)
        }

        @Test
        fun `should allow re-appending the same slot after clear`() {
            val index = ConsumerRecordIndex(capacity = 3)
            index.onAppend(0, 100L, 0)
            index.onClear()
            index.onAppend(0, 999L, 5)

            assertEquals(1, index.size)
            assertEquals(listOf(0), index.timestampRangeBitSet(999L, 999L).toSlotList())
            assertEquals(listOf(0), index.partitionBitSet(setOf(5)).toSlotList())
        }
    }

    @Nested
    inner class Construction {
        @Test
        fun `should reject non-positive capacity`() {
            assertThrows(IllegalArgumentException::class.java) { ConsumerRecordIndex(0) }
        }
    }
}
