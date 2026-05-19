package io.confluent.intellijplugin.consumer.data

import it.unimi.dsi.fastutil.ints.IntArrayList
import java.util.BitSet
import java.util.TreeMap

/**
 * Indexes [CircularBuffer] slot indices by timestamp and partition for fast filtering, search,
 * and range queries.
 *
 * **Backing structures:**
 *  - **Timestamp:** [TreeMap] from `timestamp` → list of slots holding records with that timestamp.
 *    Natural sort order powers `subMap` range queries and newest-first traversal via
 *    `descendingMap`.
 *  - **Partition:** plain [HashMap] from partition id → list of slots.
 *  - **Inverse mapping:** `slotTimestamps: LongArray` + `slotPartitions: IntArray` keyed by slot,
 *    with `occupied: BitSet` tracking which slots are live. Lets [onAppend] / [onEvict] discover
 *    the previous keys for a slot internally, so callers only report the slot.
 *
 * **Filter composition is external.** Callers build BitSets via the helper methods and AND them
 * together themselves. The index never owns search state. This mirrors VS Code's `Stream` design
 * and lets future histogram (4.2) and pagination (5.1) consumers compose filters without coupling
 * to specific filter types.
 *
 * **Threading: not thread-safe.** All access must be serialized on a single thread (the EDT, in
 * the current message-viewer wiring). Off-EDT consumers — debounced search rebuilds, future
 * histogram aggregation — must take an EDT-side snapshot before handing data to a worker thread,
 * the way [io.confluent.intellijplugin.consumer.editor.KafkaRecordsOutput] does for search.
 */
class ConsumerRecordIndex(private val capacity: Int) {

    init {
        require(capacity > 0) { "capacity must be positive, got $capacity" }
    }

    private val timestampIndex: TreeMap<Long, IntArrayList> = TreeMap()
    private val timestampDescendingView: NavigableMapView = timestampIndex.descendingMap()
    private val partitionIndex: MutableMap<Int, IntArrayList> = HashMap()

    private val slotTimestamps = LongArray(capacity)
    private val slotPartitions = IntArray(capacity)
    private val occupied = BitSet(capacity)
    private var entryCount: Int = 0

    /** Number of slots currently indexed. */
    val size: Int get() = entryCount

    /**
     * Record an append into the buffer. If the slot was previously occupied (buffer wrap),
     * the prior keys are removed first using the index's own inverse mapping — callers do not
     * need to track or report them.
     */
    fun onAppend(slot: Int, timestamp: Long, partition: Int) {
        if (occupied.get(slot)) {
            removeFromTimestamp(slot, slotTimestamps[slot])
            removeFromPartition(slot, slotPartitions[slot])
            entryCount--
        }
        addToTimestamp(slot, timestamp)
        addToPartition(slot, partition)
        slotTimestamps[slot] = timestamp
        slotPartitions[slot] = partition
        occupied.set(slot)
        entryCount++
    }

    /**
     * Remove the given slot from the index. For soft-cap evictions where the slot is freed
     * without a replacement append. No-op when the slot is already empty.
     */
    fun onEvict(slot: Int) {
        if (!occupied.get(slot)) return
        removeFromTimestamp(slot, slotTimestamps[slot])
        removeFromPartition(slot, slotPartitions[slot])
        occupied.clear(slot)
        entryCount--
    }

    fun onClear() {
        timestampIndex.clear()
        partitionIndex.clear()
        occupied.clear()
        entryCount = 0
    }

    /** BitSet of slots whose timestamp falls within `[loMs, hiMs]` (inclusive on both ends). */
    fun timestampRangeBitSet(loMs: Long, hiMs: Long): BitSet {
        val result = BitSet(capacity)
        if (loMs > hiMs) return result
        for ((_, slots) in timestampIndex.subMap(loMs, true, hiMs, true)) {
            forEachSlot(slots) { slot -> result.set(slot) }
        }
        return result
    }

    /** BitSet of slots belonging to any of the given partitions. */
    fun partitionBitSet(partitions: Collection<Int>): BitSet {
        val result = BitSet(capacity)
        for (partition in partitions) {
            val slots = partitionIndex[partition] ?: continue
            forEachSlot(slots) { slot -> result.set(slot) }
        }
        return result
    }

    /**
     * Return up to [limit] slot indices in newest-first timestamp order, after skipping [offset]
     * matching slots. When [includes] is non-null, only slots with that bit set count.
     */
    fun slice(offset: Int, limit: Int, includes: BitSet?): IntArray {
        require(offset >= 0) { "offset must be non-negative" }
        require(limit >= 0) { "limit must be non-negative" }
        if (limit == 0) return IntArray(0)

        val collected = IntArray(limit)
        var collectedCount = 0
        var skipped = 0
        walkTimestampDescending(includes) { slot, _ ->
            if (skipped < offset) {
                skipped++
                true
            } else {
                collected[collectedCount++] = slot
                collectedCount < limit
            }
        }
        return if (collectedCount == limit) collected else collected.copyOf(collectedCount)
    }

    /**
     * Walk all indexed slots in newest-first timestamp order. [action] returns `false` to stop
     * early. When [includes] is non-null, skip any slot whose bit is unset.
     */
    fun walkTimestampDescending(
        includes: BitSet?,
        action: (slot: Int, timestamp: Long) -> Boolean,
    ) {
        for ((timestamp, slots) in timestampDescendingView) {
            val slotArray = slots.elements()
            val slotSize = slots.size
            for (i in 0 until slotSize) {
                val slot = slotArray[i]
                if (includes != null && !includes[slot]) continue
                if (!action(slot, timestamp)) return
            }
        }
    }

    private fun addToTimestamp(slot: Int, timestamp: Long) {
        val bucket = timestampIndex[timestamp]
        if (bucket != null) {
            bucket.add(slot)
        } else {
            timestampIndex[timestamp] = IntArrayList(2).also { it.add(slot) }
        }
    }

    private fun addToPartition(slot: Int, partition: Int) {
        val bucket = partitionIndex[partition]
        if (bucket != null) {
            bucket.add(slot)
        } else {
            partitionIndex[partition] = IntArrayList(2).also { it.add(slot) }
        }
    }

    private fun removeFromTimestamp(slot: Int, timestamp: Long) {
        val slots = timestampIndex[timestamp] ?: return
        removeSlot(slots, slot)
        if (slots.isEmpty) timestampIndex.remove(timestamp)
    }

    private fun removeFromPartition(slot: Int, partition: Int) {
        val slots = partitionIndex[partition] ?: return
        removeSlot(slots, slot)
        if (slots.isEmpty) partitionIndex.remove(partition)
    }

    // Linear scan is fine in practice: high-cardinality timestamps mean buckets are tiny
    // (typically size 1). Pathological only for synthetic same-timestamp loads.
    private fun removeSlot(slots: IntArrayList, slot: Int) {
        val idx = slots.indexOf(slot)
        if (idx >= 0) slots.removeInt(idx)
    }

    private inline fun forEachSlot(slots: IntArrayList, action: (Int) -> Unit) {
        val arr = slots.elements()
        val n = slots.size
        for (i in 0 until n) action(arr[i])
    }
}

/**
 * Build a BitSet flagging buffer slots whose value matches [matcher].
 *
 * Reads [CircularBuffer.head] / `size` / `capacity` non-atomically; callers must serialize this
 * with buffer mutation. The production search path takes an EDT-side snapshot before handing off
 * to a worker thread — this helper is intended for that single-threaded code path and for tests.
 */
fun <T : Any> buildSearchBitSet(buffer: CircularBuffer<T>, matcher: (T) -> Boolean): BitSet {
    val result = BitSet(buffer.capacity)
    val head = buffer.head
    val capacity = buffer.capacity
    val size = buffer.size
    for (i in 0 until size) {
        val slot = (head + i) % capacity
        val value = buffer.get(slot) ?: continue
        if (matcher(value)) result.set(slot)
    }
    return result
}

private typealias NavigableMapView = java.util.NavigableMap<Long, IntArrayList>
