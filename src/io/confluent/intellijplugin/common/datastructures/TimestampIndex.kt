package io.confluent.intellijplugin.common.datastructures

import java.util.concurrent.ConcurrentSkipListMap

/**
 * Thread-safe index for O(log n) timestamp-based queries.
 *
 * Uses [ConcurrentSkipListMap] which provides:
 * - O(log n) insertion
 * - O(log n) lookup
 * - O(log n + k) range queries where k is the result size
 * - Thread-safe operations without external synchronization
 *
 * This is used to efficiently find messages within a time range without
 * scanning the entire message buffer.
 *
 * Note: Timestamps are assumed to be unique. If multiple messages have the same
 * timestamp, only the last one will be indexed. For non-unique timestamps,
 * consider using a composite key (timestamp + sequence number).
 */
class TimestampIndex {

    // Key: timestamp (epoch millis), Value: buffer index
    private val index = ConcurrentSkipListMap<Long, Int>()

    /**
     * Adds a timestamp -> buffer index mapping.
     *
     * @param timestamp The message timestamp (epoch millis)
     * @param bufferIndex The index in the message buffer
     */
    fun insert(timestamp: Long, bufferIndex: Int) {
        index[timestamp] = bufferIndex
    }

    /**
     * Removes a timestamp from the index.
     *
     * @param timestamp The timestamp to remove
     * @return The buffer index that was mapped, or null if not found
     */
    fun remove(timestamp: Long): Int? {
        return index.remove(timestamp)
    }

    /**
     * Finds the buffer index for an exact timestamp.
     *
     * @param timestamp The timestamp to find
     * @return The buffer index, or null if not found
     */
    fun find(timestamp: Long): Int? {
        return index[timestamp]
    }

    /**
     * Returns buffer indices for messages in the given time range.
     *
     * @param startInclusive Start of range (inclusive)
     * @param endExclusive End of range (exclusive)
     * @return Collection of buffer indices in the range
     */
    fun rangeQuery(startInclusive: Long, endExclusive: Long): Collection<Int> {
        return index.subMap(startInclusive, endExclusive).values
    }

    /**
     * Returns buffer indices for messages in the given time range (both inclusive).
     *
     * @param startInclusive Start of range (inclusive)
     * @param endInclusive End of range (inclusive)
     * @return Collection of buffer indices in the range
     */
    fun rangeQueryInclusive(startInclusive: Long, endInclusive: Long): Collection<Int> {
        return index.subMap(startInclusive, true, endInclusive, true).values
    }

    /**
     * Returns buffer indices for messages at or after the given timestamp.
     *
     * @param timestamp The minimum timestamp (inclusive)
     * @param limit Maximum number of results to return
     * @return List of buffer indices
     */
    fun findFrom(timestamp: Long, limit: Int): List<Int> {
        return index.tailMap(timestamp).values.take(limit)
    }

    /**
     * Returns buffer indices for messages before the given timestamp.
     *
     * @param timestamp The maximum timestamp (exclusive)
     * @param limit Maximum number of results to return
     * @return List of buffer indices
     */
    fun findBefore(timestamp: Long, limit: Int): List<Int> {
        return index.headMap(timestamp).values.toList().takeLast(limit)
    }

    /**
     * Returns the timestamp of the oldest indexed message.
     *
     * @return The minimum timestamp, or null if index is empty
     */
    fun oldestTimestamp(): Long? {
        return index.firstEntry()?.key
    }

    /**
     * Returns the timestamp of the newest indexed message.
     *
     * @return The maximum timestamp, or null if index is empty
     */
    fun newestTimestamp(): Long? {
        return index.lastEntry()?.key
    }

    /**
     * Returns the buffer index for the oldest message.
     *
     * @return The buffer index, or null if index is empty
     */
    fun oldestIndex(): Int? {
        return index.firstEntry()?.value
    }

    /**
     * Returns the buffer index for the newest message.
     *
     * @return The buffer index, or null if index is empty
     */
    fun newestIndex(): Int? {
        return index.lastEntry()?.value
    }

    /**
     * Returns the number of indexed timestamps.
     */
    fun size(): Int = index.size

    /**
     * Returns true if the index is empty.
     */
    fun isEmpty(): Boolean = index.isEmpty()

    /**
     * Clears all entries from the index.
     */
    fun clear() {
        index.clear()
    }

    /**
     * Returns all timestamps in sorted order.
     */
    fun timestamps(): Set<Long> = index.keys.toSet()

    /**
     * Returns all buffer indices in timestamp order.
     */
    fun indicesInOrder(): Collection<Int> = index.values

    /**
     * Bulk insert multiple timestamp -> index mappings.
     *
     * @param entries Pairs of (timestamp, bufferIndex)
     */
    fun insertAll(entries: Iterable<Pair<Long, Int>>) {
        entries.forEach { (timestamp, bufferIndex) ->
            index[timestamp] = bufferIndex
        }
    }

    /**
     * Removes all entries with timestamps before the given cutoff.
     *
     * Useful for eviction when the buffer wraps around.
     *
     * @param cutoffTimestamp Remove entries with timestamp < cutoff
     * @return Number of entries removed
     */
    fun removeOlderThan(cutoffTimestamp: Long): Int {
        val toRemove = index.headMap(cutoffTimestamp).keys.toList()
        toRemove.forEach { index.remove(it) }
        return toRemove.size
    }

    /**
     * Updates buffer indices after eviction.
     *
     * When elements are evicted from the front of the buffer, all indices
     * need to be decremented.
     *
     * @param decrementBy Amount to subtract from each index
     * @param removeNegative If true, remove entries that would become negative
     */
    fun adjustIndices(decrementBy: Int, removeNegative: Boolean = true) {
        val entries = index.entries.toList()
        index.clear()

        entries.forEach { (timestamp, bufferIndex) ->
            val newIndex = bufferIndex - decrementBy
            if (newIndex >= 0 || !removeNegative) {
                index[timestamp] = newIndex
            }
        }
    }

    override fun toString(): String = "TimestampIndex(size=${size()})"
}
