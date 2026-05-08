package io.confluent.intellijplugin.consumer.data

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CircularBufferTest {

    @Nested
    inner class Append {

        @Test
        fun `should fill slots 0 through n-1 below capacity with no eviction`() {
            val buffer = CircularBuffer<String>(capacity = 3)

            val a = buffer.append("a")
            val b = buffer.append("b")
            val c = buffer.append("c")

            assertEquals(CircularBuffer.SlotChange(0, null), a)
            assertEquals(CircularBuffer.SlotChange(1, null), b)
            assertEquals(CircularBuffer.SlotChange(2, null), c)
            assertEquals(3, buffer.size)
            assertEquals(0, buffer.head)
        }

        @Test
        fun `should wrap and report evicted oldest at capacity`() {
            val buffer = CircularBuffer<String>(capacity = 3)
            buffer.append("a")
            buffer.append("b")
            buffer.append("c")

            val change = buffer.append("d")

            assertEquals(0, change.slot)
            assertEquals("a", change.evicted)
            assertEquals(3, buffer.size)
            assertEquals(1, buffer.head)
        }

        @Test
        fun `should continue evicting FIFO after wrap`() {
            val buffer = CircularBuffer<String>(capacity = 3)
            listOf("a", "b", "c", "d", "e").forEach { buffer.append(it) }

            val change = buffer.append("f")

            assertEquals(2, change.slot)
            assertEquals("c", change.evicted)
            assertEquals(3, buffer.size)
            assertEquals(0, buffer.head)
            assertEquals(listOf("d", "e", "f"), buffer.toList())
        }
    }

    @Nested
    inner class Get {

        @Test
        fun `should return value for live slot`() {
            val buffer = CircularBuffer<String>(capacity = 3)
            buffer.append("a")
            buffer.append("b")

            assertEquals("a", buffer.get(0))
            assertEquals("b", buffer.get(1))
        }

        @Test
        fun `should return null for unwritten slot`() {
            val buffer = CircularBuffer<String>(capacity = 3)
            buffer.append("a")

            assertNull(buffer.get(1))
            assertNull(buffer.get(2))
        }

        @Test
        fun `should return null for out-of-range slot`() {
            val buffer = CircularBuffer<String>(capacity = 3)
            buffer.append("a")

            assertNull(buffer.get(-1))
            assertNull(buffer.get(99))
        }
    }

    @Nested
    inner class Iteration {

        @Test
        fun `should yield oldest to newest before wrap`() {
            val buffer = CircularBuffer<String>(capacity = 3)
            buffer.append("a")
            buffer.append("b")
            buffer.append("c")

            assertEquals(listOf("a", "b", "c"), buffer.toList())
        }

        @Test
        fun `should yield oldest to newest after wrap`() {
            val buffer = CircularBuffer<String>(capacity = 3)
            // Fill, wrap once, wrap again
            listOf("a", "b", "c", "d", "e").forEach { buffer.append(it) }

            assertEquals(listOf("c", "d", "e"), buffer.toList())
        }

        @Test
        fun `iterator should throw when exhausted`() {
            val buffer = CircularBuffer<String>(capacity = 2)
            buffer.append("a")
            val iter = buffer.iterator()
            iter.next()
            assertThrows<NoSuchElementException> { iter.next() }
        }

        @Test
        fun `iterator should be a stable snapshot when buffer mutates mid-iteration`() {
            val buffer = CircularBuffer<String>(capacity = 3)
            buffer.append("a")
            buffer.append("b")

            val collected = mutableListOf<String>()
            for (item in buffer) {
                if (item == "a") buffer.append("c")
                collected.add(item)
            }
            // Iterator snapshots size=2 at creation; the mid-loop append doesn't leak in.
            assertEquals(listOf("a", "b"), collected)
        }
    }

    @Nested
    inner class RemoveHead {

        @Test
        fun `should remove and return oldest live entry without appending`() {
            val buffer = CircularBuffer<String>(capacity = 3)
            buffer.append("a")
            buffer.append("b")
            buffer.append("c")

            assertEquals("a", buffer.removeHead())

            assertEquals(2, buffer.size)
            assertEquals(1, buffer.head)
            assertNull(buffer.get(0))
            assertEquals(listOf("b", "c"), buffer.toList())
        }

        @Test
        fun `should advance head across wrap`() {
            val buffer = CircularBuffer<String>(capacity = 3)
            buffer.append("a")
            buffer.append("b")
            buffer.removeHead()  // head=1
            buffer.removeHead()  // head=2
            buffer.append("c")   // slot 2

            assertEquals("c", buffer.removeHead())  // head wraps from 2 to 0
            assertEquals(0, buffer.head)
            assertEquals(0, buffer.size)
        }

        @Test
        fun `should return null on empty buffer`() {
            val buffer = CircularBuffer<String>(capacity = 2)
            assertNull(buffer.removeHead())
        }
    }

    @Nested
    inner class Clear {

        @Test
        fun `should reset size and head`() {
            val buffer = CircularBuffer<String>(capacity = 3)
            listOf("a", "b", "c", "d").forEach { buffer.append(it) }
            assertEquals(1, buffer.head)
            assertEquals(3, buffer.size)

            buffer.clear()

            assertEquals(0, buffer.head)
            assertEquals(0, buffer.size)
            assertNull(buffer.get(0))
            assertEquals(emptyList<String>(), buffer.toList())
        }

        @Test
        fun `should allow reuse after clear`() {
            val buffer = CircularBuffer<String>(capacity = 2)
            buffer.append("a")
            buffer.clear()

            val change = buffer.append("x")

            assertEquals(0, change.slot)
            assertNull(change.evicted)
            assertEquals(listOf("x"), buffer.toList())
        }
    }

    @Nested
    inner class Construction {

        @Test
        fun `should reject non-positive capacity`() {
            assertThrows<IllegalArgumentException> { CircularBuffer<String>(capacity = 0) }
            assertThrows<IllegalArgumentException> { CircularBuffer<String>(capacity = -1) }
        }
    }
}
