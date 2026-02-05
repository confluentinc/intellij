package io.confluent.intellijplugin.common.datastructures

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class TimestampIndexTest {

    private lateinit var index: TimestampIndex

    @BeforeEach
    fun setUp() {
        index = TimestampIndex()
    }

    @Nested
    inner class BasicOperations {

        @Test
        fun `should start empty`() {
            assertTrue(index.isEmpty())
            assertEquals(0, index.size())
        }

        @Test
        fun `should insert and find entries`() {
            index.insert(1000L, 0)
            index.insert(2000L, 1)
            index.insert(3000L, 2)

            assertEquals(3, index.size())
            assertEquals(0, index.find(1000L))
            assertEquals(1, index.find(2000L))
            assertEquals(2, index.find(3000L))
        }

        @Test
        fun `should return null for missing timestamps`() {
            index.insert(1000L, 0)

            assertNull(index.find(999L))
            assertNull(index.find(1001L))
        }

        @Test
        fun `should remove entries`() {
            index.insert(1000L, 0)
            index.insert(2000L, 1)

            assertEquals(0, index.remove(1000L))
            assertNull(index.find(1000L))
            assertEquals(1, index.size())
        }

        @Test
        fun `should overwrite duplicate timestamps`() {
            index.insert(1000L, 0)
            index.insert(1000L, 5)

            assertEquals(1, index.size())
            assertEquals(5, index.find(1000L))
        }
    }

    @Nested
    inner class RangeQueries {

        @BeforeEach
        fun populate() {
            // Insert timestamps 1000, 2000, 3000, ..., 10000
            for (i in 1..10) {
                index.insert(i * 1000L, i - 1)
            }
        }

        @Test
        fun `should perform exclusive end range query`() {
            val result = index.rangeQuery(2000L, 5000L)

            assertEquals(listOf(1, 2, 3), result.toList())
        }

        @Test
        fun `should perform inclusive range query`() {
            val result = index.rangeQueryInclusive(2000L, 5000L)

            assertEquals(listOf(1, 2, 3, 4), result.toList())
        }

        @Test
        fun `should return empty for non-matching range`() {
            val result = index.rangeQuery(500L, 900L)

            assertTrue(result.isEmpty())
        }

        @Test
        fun `should handle single-element range`() {
            val result = index.rangeQueryInclusive(3000L, 3000L)

            assertEquals(listOf(2), result.toList())
        }

        @Test
        fun `should find from timestamp with limit`() {
            val result = index.findFrom(5000L, 3)

            assertEquals(listOf(4, 5, 6), result)
        }

        @Test
        fun `should find before timestamp with limit`() {
            val result = index.findBefore(5000L, 2)

            assertEquals(listOf(2, 3), result)
        }
    }

    @Nested
    inner class BoundaryQueries {

        @BeforeEach
        fun populate() {
            index.insert(1000L, 0)
            index.insert(2000L, 1)
            index.insert(3000L, 2)
        }

        @Test
        fun `should find oldest timestamp`() {
            assertEquals(1000L, index.oldestTimestamp())
        }

        @Test
        fun `should find newest timestamp`() {
            assertEquals(3000L, index.newestTimestamp())
        }

        @Test
        fun `should find oldest index`() {
            assertEquals(0, index.oldestIndex())
        }

        @Test
        fun `should find newest index`() {
            assertEquals(2, index.newestIndex())
        }

        @Test
        fun `should return null for empty index boundaries`() {
            val emptyIndex = TimestampIndex()

            assertNull(emptyIndex.oldestTimestamp())
            assertNull(emptyIndex.newestTimestamp())
            assertNull(emptyIndex.oldestIndex())
            assertNull(emptyIndex.newestIndex())
        }
    }

    @Nested
    inner class BulkOperations {

        @Test
        fun `should insert all entries`() {
            val entries = listOf(
                1000L to 0,
                2000L to 1,
                3000L to 2
            )

            index.insertAll(entries)

            assertEquals(3, index.size())
            assertEquals(0, index.find(1000L))
            assertEquals(1, index.find(2000L))
            assertEquals(2, index.find(3000L))
        }

        @Test
        fun `should remove entries older than cutoff`() {
            index.insertAll(listOf(
                1000L to 0,
                2000L to 1,
                3000L to 2,
                4000L to 3
            ))

            val removed = index.removeOlderThan(2500L)

            assertEquals(2, removed)
            assertEquals(2, index.size())
            assertNull(index.find(1000L))
            assertNull(index.find(2000L))
            assertEquals(2, index.find(3000L))
        }

        @Test
        fun `should adjust indices after eviction`() {
            index.insertAll(listOf(
                1000L to 0,
                2000L to 1,
                3000L to 2,
                4000L to 3
            ))

            // Simulate evicting first 2 elements from buffer
            index.adjustIndices(2, removeNegative = true)

            assertEquals(2, index.size())
            assertNull(index.find(1000L)) // Was 0, now negative, removed
            assertNull(index.find(2000L)) // Was 1, now negative, removed
            assertEquals(0, index.find(3000L)) // Was 2, now 0
            assertEquals(1, index.find(4000L)) // Was 3, now 1
        }

        @Test
        fun `should clear all entries`() {
            index.insertAll(listOf(1000L to 0, 2000L to 1))

            index.clear()

            assertTrue(index.isEmpty())
        }
    }

    @Nested
    inner class OrderPreservation {

        @Test
        fun `should return timestamps in sorted order`() {
            // Insert out of order
            index.insert(3000L, 2)
            index.insert(1000L, 0)
            index.insert(2000L, 1)

            val timestamps = index.timestamps().toList()
            assertEquals(listOf(1000L, 2000L, 3000L), timestamps)
        }

        @Test
        fun `should return indices in timestamp order`() {
            index.insert(3000L, 2)
            index.insert(1000L, 0)
            index.insert(2000L, 1)

            val indices = index.indicesInOrder().toList()
            assertEquals(listOf(0, 1, 2), indices)
        }
    }

    @Nested
    inner class ConcurrencySupport {

        @Test
        fun `should handle concurrent insertions`() {
            val threads = (0 until 10).map { threadId ->
                Thread {
                    repeat(1000) { i ->
                        val timestamp = threadId * 10000L + i
                        index.insert(timestamp, i)
                    }
                }
            }

            threads.forEach { it.start() }
            threads.forEach { it.join() }

            // Each thread overwrites the same indices 0-999, but timestamps are unique
            // So we should have 10 * 1000 = 10000 unique timestamps
            assertEquals(10_000, index.size())
        }
    }
}
