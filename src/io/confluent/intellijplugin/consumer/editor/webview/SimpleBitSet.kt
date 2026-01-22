package io.confluent.intellijplugin.consumer.editor.webview

/**
 * Simple BitSet implementation for efficient filtering.
 * Based on VS Code's BitSet (stream.ts:373-433).
 * Uses Uint32-style bit manipulation for fast operations.
 */
class SimpleBitSet(val capacity: Int) {
    private val bits = IntArray((capacity + 31) / 32)

    /** Set a bit to 1 at index. */
    fun set(index: Int) {
        if (index >= capacity) return
        val arrayIndex = index ushr 5  // divide by 32
        val bitIndex = index and 31    // mod 32
        bits[arrayIndex] = bits[arrayIndex] or (1 shl (31 - bitIndex))
    }

    /** Set a bit to 0 at index. */
    fun unset(index: Int) {
        if (index >= capacity) return
        val arrayIndex = index ushr 5
        val bitIndex = index and 31
        bits[arrayIndex] = bits[arrayIndex] and (1 shl (31 - bitIndex)).inv()
    }

    /** Check if an index is set. */
    fun includes(index: Int): Boolean {
        if (index >= capacity) return false
        val arrayIndex = index ushr 5
        val bitIndex = index and 31
        return (bits[arrayIndex] and (1 shl (31 - bitIndex))) != 0
    }

    /** Create a predicate function for faster repeated checks. */
    fun predicate(): (Int) -> Boolean {
        val localBits = bits
        return { index ->
            if (index >= capacity) false
            else {
                val arrayIndex = index ushr 5
                val bitIndex = index and 31
                (localBits[arrayIndex] and (1 shl (31 - bitIndex))) != 0
            }
        }
    }

    /** Count number of bits set to 1. */
    fun count(): Int {
        var count = 0
        for (value in bits) {
            count += Integer.bitCount(value)
        }
        return count
    }

    /** Create a copy of this BitSet. */
    fun copy(): SimpleBitSet {
        val copy = SimpleBitSet(capacity)
        bits.copyInto(copy.bits)
        return copy
    }

    /** Perform intersection with another BitSet (modifies this). */
    fun intersection(other: SimpleBitSet): SimpleBitSet {
        val minLength = minOf(bits.size, other.bits.size)
        for (i in 0 until minLength) {
            bits[i] = bits[i] and other.bits[i]
        }
        return this
    }

    /** Reset all bits to 0. */
    fun clear() {
        bits.fill(0)
    }
}
