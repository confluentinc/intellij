package io.confluent.intellijplugin.common.datastructures

import java.util.BitSet

/**
 * Wrapper around [java.util.BitSet] for caching filter results.
 *
 * Each bit represents whether a message at that buffer index matches a filter.
 * This allows:
 * - O(1) check if a message matches
 * - O(popcount) to count matches (highly optimized in hardware)
 * - Fast AND/OR operations for combining multiple filters
 * - Memory efficient: ~125KB for 1M records (1 bit per record)
 *
 * Thread-safety: This class is NOT thread-safe. External synchronization is required
 * for concurrent access.
 *
 * @param capacity The maximum number of bits (should match buffer capacity)
 */
class FilterBitSet(private val capacity: Int) {

    init {
        require(capacity > 0) { "Capacity must be positive, got $capacity" }
    }

    private val bits = BitSet(capacity)

    /**
     * Sets the bit at the given index (marks as matching).
     */
    fun set(index: Int) {
        validateIndex(index)
        bits.set(index)
    }

    /**
     * Clears the bit at the given index (marks as not matching).
     */
    fun clear(index: Int) {
        validateIndex(index)
        bits.clear(index)
    }

    /**
     * Sets all bits in the given range.
     *
     * @param fromInclusive Start index (inclusive)
     * @param toExclusive End index (exclusive)
     */
    fun setRange(fromInclusive: Int, toExclusive: Int) {
        bits[fromInclusive, toExclusive] = true
    }

    /**
     * Clears all bits in the given range.
     *
     * @param fromInclusive Start index (inclusive)
     * @param toExclusive End index (exclusive)
     */
    fun clearRange(fromInclusive: Int, toExclusive: Int) {
        bits.clear(fromInclusive, toExclusive)
    }

    /**
     * Returns true if the bit at the given index is set.
     */
    operator fun contains(index: Int): Boolean {
        return index in 0 until capacity && bits[index]
    }

    /**
     * Returns true if the bit at the given index is set.
     */
    operator fun get(index: Int): Boolean = contains(index)

    /**
     * Sets the bit value at the given index.
     */
    operator fun set(index: Int, value: Boolean) {
        validateIndex(index)
        bits[index] = value
    }

    /**
     * Returns a new FilterBitSet with bits set only where both this AND other have bits set.
     */
    fun intersect(other: FilterBitSet): FilterBitSet {
        val result = FilterBitSet(maxOf(capacity, other.capacity))
        result.bits.or(this.bits)
        result.bits.and(other.bits)
        return result
    }

    /**
     * Modifies this FilterBitSet in place to keep only bits where both this AND other have bits set.
     */
    fun intersectInPlace(other: FilterBitSet) {
        bits.and(other.bits)
    }

    /**
     * Returns a new FilterBitSet with bits set where either this OR other has bits set.
     */
    fun union(other: FilterBitSet): FilterBitSet {
        val result = FilterBitSet(maxOf(capacity, other.capacity))
        result.bits.or(this.bits)
        result.bits.or(other.bits)
        return result
    }

    /**
     * Modifies this FilterBitSet in place to set bits where either this OR other has bits set.
     */
    fun unionInPlace(other: FilterBitSet) {
        bits.or(other.bits)
    }

    /**
     * Returns a new FilterBitSet with bits inverted (NOT operation).
     */
    fun invert(): FilterBitSet {
        val result = FilterBitSet(capacity)
        result.bits.or(this.bits)
        result.bits.flip(0, capacity)
        return result
    }

    /**
     * Inverts all bits in place (NOT operation).
     */
    fun invertInPlace() {
        bits.flip(0, capacity)
    }

    /**
     * Returns a new FilterBitSet with bits set where this has bits but other doesn't (AND NOT).
     */
    fun subtract(other: FilterBitSet): FilterBitSet {
        val result = FilterBitSet(capacity)
        result.bits.or(this.bits)
        result.bits.andNot(other.bits)
        return result
    }

    /**
     * Returns the number of bits set to true.
     *
     * This operation is highly optimized using hardware popcount instructions.
     */
    fun cardinality(): Int = bits.cardinality()

    /**
     * Returns true if no bits are set.
     */
    fun isEmpty(): Boolean = bits.isEmpty

    /**
     * Returns true if all bits are set.
     */
    fun isFull(): Boolean = bits.cardinality() == capacity

    /**
     * Clears all bits.
     */
    fun clear() {
        bits.clear()
    }

    /**
     * Sets all bits.
     */
    fun setAll() {
        bits[0, capacity] = true
    }

    /**
     * Returns the capacity (maximum number of bits).
     */
    fun capacity(): Int = capacity

    /**
     * Returns a sequence of indices where bits are set.
     *
     * This is more efficient than iterating all indices and checking each.
     */
    fun matchingIndices(): Sequence<Int> = sequence {
        var i = bits.nextSetBit(0)
        while (i >= 0) {
            yield(i)
            if (i == Int.MAX_VALUE) break
            i = bits.nextSetBit(i + 1)
        }
    }

    /**
     * Returns a list of indices where bits are set.
     */
    fun matchingIndicesList(): List<Int> = matchingIndices().toList()

    /**
     * Returns the index of the first set bit, or -1 if none are set.
     */
    fun firstSetBit(): Int = bits.nextSetBit(0)

    /**
     * Returns the index of the last set bit, or -1 if none are set.
     */
    fun lastSetBit(): Int {
        val length = bits.length()
        return if (length == 0) -1 else length - 1
    }

    /**
     * Returns the index of the next set bit at or after fromIndex, or -1 if none.
     */
    fun nextSetBit(fromIndex: Int): Int = bits.nextSetBit(fromIndex)

    /**
     * Returns the index of the next clear bit at or after fromIndex.
     */
    fun nextClearBit(fromIndex: Int): Int = bits.nextClearBit(fromIndex)

    /**
     * Shifts all bits to the left by the given amount.
     *
     * Useful when evicting elements from the front of the buffer.
     * Bits that shift past index 0 are lost.
     *
     * @param shiftAmount Number of positions to shift left
     */
    fun shiftLeft(shiftAmount: Int) {
        if (shiftAmount <= 0) return
        if (shiftAmount >= capacity) {
            bits.clear()
            return
        }

        val newBits = BitSet(capacity)

        // Rebuild with shifted indices
        var i = bits.nextSetBit(shiftAmount)
        while (i >= 0) {
            newBits.set(i - shiftAmount)
            if (i == Int.MAX_VALUE) break
            i = bits.nextSetBit(i + 1)
        }

        bits.clear()
        bits.or(newBits)
    }

    /**
     * Creates a copy of this FilterBitSet.
     */
    fun copy(): FilterBitSet {
        val result = FilterBitSet(capacity)
        result.bits.or(this.bits)
        return result
    }

    /**
     * Applies a bulk operation from a collection of indices.
     *
     * @param indices The indices to set
     */
    fun setAll(indices: Iterable<Int>) {
        indices.forEach { index ->
            if (index in 0 until capacity) {
                bits.set(index)
            }
        }
    }

    /**
     * Returns the approximate memory usage in bytes.
     */
    fun memoryUsageBytes(): Long {
        // BitSet uses long[] internally, 8 bytes per 64 bits
        return (capacity + 63L) / 64 * 8 + 32 // +32 for object overhead
    }

    private fun validateIndex(index: Int) {
        if (index < 0 || index >= capacity) {
            throw IndexOutOfBoundsException("Index $index out of bounds for capacity $capacity")
        }
    }

    override fun toString(): String = "FilterBitSet(cardinality=${cardinality()}, capacity=$capacity)"

    companion object {
        /**
         * Creates a FilterBitSet with all bits set.
         */
        fun allSet(capacity: Int): FilterBitSet {
            return FilterBitSet(capacity).apply { setAll() }
        }

        /**
         * Creates a FilterBitSet from a collection of matching indices.
         */
        fun fromIndices(capacity: Int, indices: Iterable<Int>): FilterBitSet {
            return FilterBitSet(capacity).apply { setAll(indices) }
        }
    }
}
