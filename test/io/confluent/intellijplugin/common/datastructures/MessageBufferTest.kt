package io.confluent.intellijplugin.common.datastructures

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MessageBufferTest {

    @Nested
    inner class Construction {

        @Test
        fun `should create buffer with specified capacity`() {
            val buffer = MessageBuffer<String>(100)
            assertEquals(100, buffer.capacity())
            assertEquals(0, buffer.size())
            assertTrue(buffer.isEmpty())
        }

        @Test
        fun `should reject non-positive capacity`() {
            assertThrows<IllegalArgumentException> { MessageBuffer<String>(0) }
            assertThrows<IllegalArgumentException> { MessageBuffer<String>(-1) }
        }
    }

    @Nested
    inner class AddOperations {

        @Test
        fun `should add elements without eviction when under capacity`() {
            val buffer = MessageBuffer<Int>(5)

            assertNull(buffer.add(1))
            assertNull(buffer.add(2))
            assertNull(buffer.add(3))

            assertEquals(3, buffer.size())
            assertFalse(buffer.isFull())
        }

        @Test
        fun `should evict oldest element when at capacity`() {
            val buffer = MessageBuffer<Int>(3)

            buffer.add(1)
            buffer.add(2)
            buffer.add(3)
            assertTrue(buffer.isFull())

            val evicted = buffer.add(4)
            assertEquals(1, evicted)
            assertEquals(3, buffer.size())
            assertEquals(listOf(2, 3, 4), buffer.toList())
        }

        @Test
        fun `should maintain FIFO order after multiple evictions`() {
            val buffer = MessageBuffer<Int>(3)

            // Fill buffer
            buffer.add(1)
            buffer.add(2)
            buffer.add(3)

            // Add more, causing evictions
            assertEquals(1, buffer.add(4))
            assertEquals(2, buffer.add(5))
            assertEquals(3, buffer.add(6))

            assertEquals(listOf(4, 5, 6), buffer.toList())
        }

        @Test
        fun `should handle addAll correctly`() {
            val buffer = MessageBuffer<Int>(5)

            val evicted1 = buffer.addAll(listOf(1, 2, 3))
            assertTrue(evicted1.isEmpty())
            assertEquals(3, buffer.size())

            val evicted2 = buffer.addAll(listOf(4, 5, 6, 7))
            assertEquals(listOf(1, 2), evicted2)
            assertEquals(listOf(3, 4, 5, 6, 7), buffer.toList())
        }
    }

    @Nested
    inner class AccessOperations {

        @Test
        fun `should access elements by index`() {
            val buffer = MessageBuffer<String>(5)
            buffer.addAll(listOf("a", "b", "c"))

            assertEquals("a", buffer[0])
            assertEquals("b", buffer[1])
            assertEquals("c", buffer[2])
        }

        @Test
        fun `should throw on out of bounds access`() {
            val buffer = MessageBuffer<String>(5)
            buffer.add("a")

            assertThrows<IndexOutOfBoundsException> { buffer[-1] }
            assertThrows<IndexOutOfBoundsException> { buffer[1] }
            assertThrows<IndexOutOfBoundsException> { buffer[5] }
        }

        @Test
        fun `should return correct first and last elements`() {
            val buffer = MessageBuffer<Int>(5)

            assertNull(buffer.peekFirst())
            assertNull(buffer.peekLast())

            buffer.addAll(listOf(1, 2, 3))

            assertEquals(1, buffer.peekFirst())
            assertEquals(3, buffer.peekLast())
        }

        @Test
        fun `should iterate in insertion order`() {
            val buffer = MessageBuffer<Int>(5)
            buffer.addAll(listOf(10, 20, 30))

            val collected = buffer.iterator().asSequence().toList()
            assertEquals(listOf(10, 20, 30), collected)
        }

        @Test
        fun `should provide sequence access`() {
            val buffer = MessageBuffer<Int>(5)
            buffer.addAll(listOf(1, 2, 3, 4, 5))

            val sum = buffer.asSequence().sum()
            assertEquals(15, sum)
        }
    }

    @Nested
    inner class FilterOperations {

        @Test
        fun `should find indices matching predicate`() {
            val buffer = MessageBuffer<Int>(10)
            buffer.addAll(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))

            val evenIndices = buffer.indicesWhere { it % 2 == 0 }
            assertEquals(listOf(1, 3, 5, 7, 9), evenIndices)
        }

        @Test
        fun `should map with indices`() {
            val buffer = MessageBuffer<String>(3)
            buffer.addAll(listOf("a", "b", "c"))

            val mapped = buffer.mapIndexed { index, value -> "$index:$value" }
            assertEquals(listOf("0:a", "1:b", "2:c"), mapped)
        }
    }

    @Nested
    inner class ClearOperations {

        @Test
        fun `should clear all elements`() {
            val buffer = MessageBuffer<Int>(5)
            buffer.addAll(listOf(1, 2, 3))

            buffer.clear()

            assertTrue(buffer.isEmpty())
            assertEquals(0, buffer.size())
            assertEquals(5, buffer.capacity())
        }
    }

    @Nested
    inner class EdgeCases {

        @Test
        fun `should handle capacity of 1`() {
            val buffer = MessageBuffer<String>(1)

            assertNull(buffer.add("a"))
            assertEquals("a", buffer.add("b"))
            assertEquals("b", buffer.add("c"))

            assertEquals(1, buffer.size())
            assertEquals("c", buffer[0])
        }

        @Test
        fun `should handle large number of insertions`() {
            val buffer = MessageBuffer<Int>(100)

            repeat(10_000) { i ->
                buffer.add(i)
            }

            assertEquals(100, buffer.size())
            // Should contain the last 100 elements
            assertEquals(9900, buffer.peekFirst())
            assertEquals(9999, buffer.peekLast())
        }
    }
}
