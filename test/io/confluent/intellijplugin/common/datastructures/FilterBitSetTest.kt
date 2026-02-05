package io.confluent.intellijplugin.common.datastructures

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FilterBitSetTest {

    private lateinit var bitset: FilterBitSet

    @BeforeEach
    fun setUp() {
        bitset = FilterBitSet(100)
    }

    @Nested
    inner class Construction {

        @Test
        fun `should create empty bitset with capacity`() {
            assertEquals(100, bitset.capacity())
            assertEquals(0, bitset.cardinality())
            assertTrue(bitset.isEmpty())
        }

        @Test
        fun `should reject non-positive capacity`() {
            assertThrows<IllegalArgumentException> { FilterBitSet(0) }
            assertThrows<IllegalArgumentException> { FilterBitSet(-1) }
        }

        @Test
        fun `should create from indices`() {
            val bs = FilterBitSet.fromIndices(100, listOf(5, 10, 15))

            assertEquals(3, bs.cardinality())
            assertTrue(5 in bs)
            assertTrue(10 in bs)
            assertTrue(15 in bs)
        }

        @Test
        fun `should create with all bits set`() {
            val bs = FilterBitSet.allSet(50)

            assertEquals(50, bs.cardinality())
            assertTrue(bs.isFull())
        }
    }

    @Nested
    inner class BasicOperations {

        @Test
        fun `should set and check individual bits`() {
            bitset.set(10)
            bitset.set(20)
            bitset.set(30)

            assertTrue(10 in bitset)
            assertTrue(20 in bitset)
            assertTrue(30 in bitset)
            assertFalse(15 in bitset)
        }

        @Test
        fun `should clear bits`() {
            bitset.set(10)
            bitset.set(20)

            bitset.clear(10)

            assertFalse(10 in bitset)
            assertTrue(20 in bitset)
        }

        @Test
        fun `should handle operator get`() {
            bitset.set(5)

            assertTrue(bitset[5])
            assertFalse(bitset[6])
        }

        @Test
        fun `should handle operator set`() {
            bitset[10] = true
            bitset[20] = true
            bitset[10] = false

            assertFalse(bitset[10])
            assertTrue(bitset[20])
        }

        @Test
        fun `should throw on out of bounds set`() {
            assertThrows<IndexOutOfBoundsException> { bitset.set(-1) }
            assertThrows<IndexOutOfBoundsException> { bitset.set(100) }
        }

        @Test
        fun `should return false for out of bounds contains`() {
            assertFalse(-1 in bitset)
            assertFalse(100 in bitset)
        }
    }

    @Nested
    inner class RangeOperations {

        @Test
        fun `should set range of bits`() {
            bitset.setRange(10, 20)

            assertEquals(10, bitset.cardinality())
            for (i in 10 until 20) {
                assertTrue(i in bitset, "Bit $i should be set")
            }
            assertFalse(9 in bitset)
            assertFalse(20 in bitset)
        }

        @Test
        fun `should clear range of bits`() {
            bitset.setRange(0, 50)
            bitset.clearRange(20, 30)

            assertEquals(40, bitset.cardinality())
            for (i in 20 until 30) {
                assertFalse(i in bitset, "Bit $i should be clear")
            }
        }
    }

    @Nested
    inner class SetOperations {

        @Test
        fun `should compute intersection`() {
            val a = FilterBitSet.fromIndices(100, listOf(1, 2, 3, 4, 5))
            val b = FilterBitSet.fromIndices(100, listOf(3, 4, 5, 6, 7))

            val result = a.intersect(b)

            assertEquals(3, result.cardinality())
            assertEquals(listOf(3, 4, 5), result.matchingIndicesList())
        }

        @Test
        fun `should compute intersection in place`() {
            val a = FilterBitSet.fromIndices(100, listOf(1, 2, 3, 4, 5))
            val b = FilterBitSet.fromIndices(100, listOf(3, 4, 5, 6, 7))

            a.intersectInPlace(b)

            assertEquals(3, a.cardinality())
            assertEquals(listOf(3, 4, 5), a.matchingIndicesList())
        }

        @Test
        fun `should compute union`() {
            val a = FilterBitSet.fromIndices(100, listOf(1, 2, 3))
            val b = FilterBitSet.fromIndices(100, listOf(3, 4, 5))

            val result = a.union(b)

            assertEquals(5, result.cardinality())
            assertEquals(listOf(1, 2, 3, 4, 5), result.matchingIndicesList())
        }

        @Test
        fun `should compute union in place`() {
            val a = FilterBitSet.fromIndices(100, listOf(1, 2, 3))
            val b = FilterBitSet.fromIndices(100, listOf(3, 4, 5))

            a.unionInPlace(b)

            assertEquals(5, a.cardinality())
        }

        @Test
        fun `should compute difference`() {
            val a = FilterBitSet.fromIndices(100, listOf(1, 2, 3, 4, 5))
            val b = FilterBitSet.fromIndices(100, listOf(3, 4))

            val result = a.subtract(b)

            assertEquals(3, result.cardinality())
            assertEquals(listOf(1, 2, 5), result.matchingIndicesList())
        }

        @Test
        fun `should invert bits`() {
            bitset.setRange(0, 10)

            val inverted = bitset.invert()

            assertEquals(90, inverted.cardinality())
            for (i in 0 until 10) {
                assertFalse(i in inverted)
            }
            for (i in 10 until 100) {
                assertTrue(i in inverted)
            }
        }

        @Test
        fun `should invert in place`() {
            bitset.setRange(0, 10)

            bitset.invertInPlace()

            assertEquals(90, bitset.cardinality())
        }
    }

    @Nested
    inner class QueryOperations {

        @Test
        fun `should return matching indices as sequence`() {
            bitset.set(5)
            bitset.set(15)
            bitset.set(25)

            val indices = bitset.matchingIndices().toList()

            assertEquals(listOf(5, 15, 25), indices)
        }

        @Test
        fun `should find first and last set bits`() {
            bitset.set(10)
            bitset.set(50)
            bitset.set(90)

            assertEquals(10, bitset.firstSetBit())
            assertEquals(90, bitset.lastSetBit())
        }

        @Test
        fun `should return -1 for empty bitset boundaries`() {
            assertEquals(-1, bitset.firstSetBit())
            assertEquals(-1, bitset.lastSetBit())
        }

        @Test
        fun `should find next set bit`() {
            bitset.set(10)
            bitset.set(20)
            bitset.set(30)

            assertEquals(10, bitset.nextSetBit(0))
            assertEquals(10, bitset.nextSetBit(10))
            assertEquals(20, bitset.nextSetBit(11))
            assertEquals(30, bitset.nextSetBit(25))
            assertEquals(-1, bitset.nextSetBit(31))
        }
    }

    @Nested
    inner class ShiftOperations {

        @Test
        fun `should shift bits left`() {
            bitset.set(10)
            bitset.set(20)
            bitset.set(30)

            bitset.shiftLeft(5)

            assertFalse(10 in bitset)
            assertTrue(5 in bitset)  // Was 10
            assertTrue(15 in bitset) // Was 20
            assertTrue(25 in bitset) // Was 30
        }

        @Test
        fun `should drop bits that shift past zero`() {
            bitset.set(2)
            bitset.set(5)
            bitset.set(10)

            bitset.shiftLeft(5)

            assertEquals(2, bitset.cardinality())
            assertTrue(0 in bitset)  // Was 5
            assertTrue(5 in bitset)  // Was 10
        }

        @Test
        fun `should handle shift of zero`() {
            bitset.set(10)

            bitset.shiftLeft(0)

            assertTrue(10 in bitset)
        }

        @Test
        fun `should handle shift larger than capacity`() {
            bitset.set(10)
            bitset.set(50)

            bitset.shiftLeft(200)

            assertTrue(bitset.isEmpty())
        }
    }

    @Nested
    inner class UtilityOperations {

        @Test
        fun `should copy bitset`() {
            bitset.set(10)
            bitset.set(20)

            val copy = bitset.copy()
            bitset.clear(10)

            assertTrue(10 in copy)
            assertTrue(20 in copy)
            assertFalse(10 in bitset)
        }

        @Test
        fun `should set all and clear all`() {
            bitset.setAll()
            assertTrue(bitset.isFull())
            assertEquals(100, bitset.cardinality())

            bitset.clear()
            assertTrue(bitset.isEmpty())
            assertEquals(0, bitset.cardinality())
        }

        @Test
        fun `should bulk set from indices`() {
            bitset.setAll(listOf(5, 10, 15, 20))

            assertEquals(4, bitset.cardinality())
            assertTrue(5 in bitset)
            assertTrue(20 in bitset)
        }

        @Test
        fun `should report memory usage`() {
            val smallBitset = FilterBitSet(64)
            val largeBitset = FilterBitSet(1_000_000)

            // 64 bits = 1 long = 8 bytes + overhead
            assertTrue(smallBitset.memoryUsageBytes() >= 8)

            // 1M bits = ~15625 longs = ~125KB + overhead
            val largeMemory = largeBitset.memoryUsageBytes()
            assertTrue(largeMemory > 100_000, "Expected >100KB, got $largeMemory")
            assertTrue(largeMemory < 200_000, "Expected <200KB, got $largeMemory")
        }
    }

    @Nested
    inner class LargeScale {

        @Test
        fun `should handle million-bit operations`() {
            val largeBitset = FilterBitSet(1_000_000)

            // Set every 100th bit
            for (i in 0 until 1_000_000 step 100) {
                largeBitset.set(i)
            }

            assertEquals(10_000, largeBitset.cardinality())

            // Verify iteration works
            val indices = largeBitset.matchingIndices().take(5).toList()
            assertEquals(listOf(0, 100, 200, 300, 400), indices)
        }
    }
}
