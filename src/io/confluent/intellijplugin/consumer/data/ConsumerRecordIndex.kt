package io.confluent.intellijplugin.consumer.data

import java.util.BitSet
import java.util.NavigableMap
import java.util.concurrent.ConcurrentSkipListMap

/**
 * Identifies the keys held by a record that is about to be evicted from a [CircularBuffer].
 * Required so [ConsumerRecordIndex.onAppend] can remove the old entry before reinserting at the
 * same slot.
 */
data class PrevKeys(val timestamp: Long, val partition: Int)

/**
 * Indexes [CircularBuffer] slot indices by timestamp and partition.
 *
 * Backing structures:
 *  - **Timestamp:** [ConcurrentSkipListMap] from `timestamp` → list of slots holding records with
 *    that timestamp. The natural sort order powers `subMap` range queries and newest-first
 *    traversal via `descendingMap`.
 *  - **Partition:** plain [HashMap] from partition id → list of slots.
 *
 * Filter composition is intentionally **external**: callers build BitSets via the helper
 * methods on this class (and a free-text builder elsewhere) and AND them together themselves.
 * The index never owns search state.
 *
 * Not thread-safe; callers serialize access (typically EDT for writes, background thread for
 * read-only BitSet builds during a debounce window).
 */
class ConsumerRecordIndex(private val capacity: Int) {

    init {
        require(capacity > 0) { "capacity must be positive, got $capacity" }
    }

    private val timestampIndex: ConcurrentSkipListMap<Long, MutableList<Int>> = ConcurrentSkipListMap()
    private val timestampDescendingView: NavigableMap<Long, MutableList<Int>> = timestampIndex.descendingMap()
    private val partitionIndex: MutableMap<Int, MutableList<Int>> = HashMap()
    private var entryCount: Int = 0

    /** Number of slots currently indexed. */
    val size: Int get() = entryCount

    /**
     * Record an append into the buffer. If [evicted] is non-null, its keys are removed first
     * — eviction happens when the buffer wraps and the same slot is being reused.
     */
    fun onAppend(slot: Int, timestamp: Long, partition: Int, evicted: PrevKeys?) {
        if (evicted != null) {
            removeFromTimestamp(slot, evicted.timestamp)
            removeFromPartition(slot, evicted.partition)
            entryCount--
        }
        timestampIndex.computeIfAbsent(timestamp) { ArrayList(2) }.add(slot)
        partitionIndex.computeIfAbsent(partition) { ArrayList(2) }.add(slot)
        entryCount++
    }

    /**
     * Remove the given slot's keys without reinserting. For pure soft-cap evictions where the
     * slot is freed but no replacement record is added at the same slot.
     */
    fun onEvict(slot: Int, timestamp: Long, partition: Int) {
        removeFromTimestamp(slot, timestamp)
        removeFromPartition(slot, partition)
        entryCount--
    }

    fun onClear() {
        timestampIndex.clear()
        partitionIndex.clear()
        entryCount = 0
    }

    /** BitSet of slots whose timestamp falls within `[loMs, hiMs]` (inclusive on both ends). */
    fun timestampRangeBitSet(loMs: Long, hiMs: Long): BitSet {
        val result = BitSet(capacity)
        if (loMs > hiMs) return result
        for ((_, slots) in timestampIndex.subMap(loMs, true, hiMs, true)) {
            for (slot in slots) result.set(slot)
        }
        return result
    }

    /** BitSet of slots belonging to any of the given partitions. */
    fun partitionBitSet(partitions: Collection<Int>): BitSet {
        val result = BitSet(capacity)
        for (partition in partitions) {
            val slots: List<Int> = partitionIndex[partition] ?: continue
            for (slot in slots) result.set(slot)
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
            for (slot in slots) {
                if (includes != null && !includes[slot]) continue
                if (!action(slot, timestamp)) return
            }
        }
    }

    private fun removeFromTimestamp(slot: Int, timestamp: Long) {
        val slots = timestampIndex[timestamp] ?: return
        removeSlot(slots, slot)
        if (slots.isEmpty()) timestampIndex.remove(timestamp)
    }

    private fun removeFromPartition(slot: Int, partition: Int) {
        val slots = partitionIndex[partition] ?: return
        removeSlot(slots, slot)
        if (slots.isEmpty()) partitionIndex.remove(partition)
    }

    // MutableList<Int>.remove(Int) is ambiguous on the JVM with java.util.List.remove(int) which
    // removes by index. Look up the index explicitly to force value-based removal.
    private fun removeSlot(slots: MutableList<Int>, slot: Int) {
        val idx = slots.indexOf(slot)
        if (idx >= 0) slots.removeAt(idx)
    }
}

/**
 * Build a BitSet flagging buffer slots whose value matches [matcher]. Iterates only live entries.
 *
 * Designed to run off the EDT inside a debounced search rebuild.
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
