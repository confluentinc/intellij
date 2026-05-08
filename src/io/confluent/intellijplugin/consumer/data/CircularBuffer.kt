package io.confluent.intellijplugin.consumer.data

/**
 * Fixed-capacity ring buffer with stable slot indices.
 *
 * `append` returns the slot the value was written into and, once full, the value it evicted.
 * Slot indices are stable for the lifetime of an entry — slot N is always slot N until the
 * buffer wraps and overwrites it. Iteration yields entries in insertion order (oldest → newest).
 *
 * Not thread-safe — single-writer only. Callers are responsible for choosing and enforcing a
 * threading model; this class deliberately does not lock or assert so it stays a plain
 * primitive. The owning component should document where mutations happen and how readers see
 * a consistent view.
 */
class CircularBuffer<T : Any>(val capacity: Int) : Iterable<T> {

    init {
        require(capacity > 0) { "capacity must be positive, got $capacity" }
    }

    private val slots: Array<Any?> = arrayOfNulls(capacity)
    private var headSlot: Int = 0
    private var fillCount: Int = 0

    /** Slot of the oldest live element. Meaningful only when [size] > 0. */
    val head: Int get() = headSlot

    /** Number of live elements currently in the buffer; never exceeds [capacity]. */
    val size: Int get() = fillCount

    /**
     * Append [value], returning the slot it was written into and any evicted predecessor.
     * Once the buffer is full, every append evicts the oldest live entry.
     */
    fun append(value: T): SlotChange<T> {
        val slot: Int
        val evicted: T?
        if (fillCount < capacity) {
            slot = (headSlot + fillCount) % capacity
            evicted = null
            fillCount++
        } else {
            slot = headSlot
            @Suppress("UNCHECKED_CAST")
            evicted = slots[slot] as T?
            headSlot = (headSlot + 1) % capacity
        }
        slots[slot] = value
        return SlotChange(slot, evicted)
    }

    /** Returns the value stored at [slot], or `null` if the slot is empty or out of range. */
    @Suppress("UNCHECKED_CAST")
    fun get(slot: Int): T? {
        if (slot < 0 || slot >= capacity) return null
        return slots[slot] as T?
    }

    /**
     * Remove and return the oldest live entry without appending a replacement. Useful for
     * soft-cap eviction when a higher-level cap is below the buffer capacity. Returns `null`
     * when the buffer is empty.
     */
    fun removeHead(): T? {
        if (fillCount == 0) return null
        @Suppress("UNCHECKED_CAST")
        val removed = slots[headSlot] as T?
        slots[headSlot] = null
        headSlot = (headSlot + 1) % capacity
        fillCount--
        return removed
    }

    /** Remove all entries. */
    fun clear() {
        java.util.Arrays.fill(slots, null)
        headSlot = 0
        fillCount = 0
    }

    /**
     * Yields live entries in insertion order, oldest first. Snapshots `head` and `size` at
     * iterator creation so a mid-iteration `removeHead` or `clear` doesn't shift slot positions
     * under the iterator. Values themselves are NOT snapshotted: if a writer `append`s while the
     * buffer is full, the slot at `snapshotHead` is overwritten in place and the iterator will
     * yield the new value rather than the evicted one. Given the single-writer EDT contract this
     * is fine for in-EDT iteration; off-EDT consumers should copy into their own snapshot.
     */
    override fun iterator(): Iterator<T> = object : Iterator<T> {
        private val snapshotHead = headSlot
        private val snapshotSize = fillCount
        private var visited = 0
        override fun hasNext(): Boolean = visited < snapshotSize
        @Suppress("UNCHECKED_CAST")
        override fun next(): T {
            check(hasNext()) { "no more elements" }
            val slot = (snapshotHead + visited) % capacity
            visited++
            return slots[slot] as T
        }
    }

    data class SlotChange<T>(val slot: Int, val evicted: T?)
}
