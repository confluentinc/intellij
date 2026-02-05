package io.confluent.intellijplugin.common.datastructures

import java.util.ArrayDeque

/**
 * Fixed-size circular buffer for storing messages with O(1) insertion and eviction.
 *
 * Unlike ArrayList-based implementations, this uses ArrayDeque which provides:
 * - O(1) addLast (insertion)
 * - O(1) removeFirst (eviction)
 * - O(1) random access via iterator
 *
 * Thread-safety: This class is NOT thread-safe. External synchronization is required
 * for concurrent access, or use with a single writer thread.
 *
 * @param T The type of elements stored in the buffer
 * @param capacity Maximum number of elements before eviction occurs
 */
class MessageBuffer<T>(private val capacity: Int) {

    init {
        require(capacity > 0) { "Capacity must be positive, got $capacity" }
    }

    private val buffer = ArrayDeque<T>(capacity)

    /**
     * Adds an element to the buffer.
     *
     * If the buffer is at capacity, the oldest element is evicted first.
     *
     * @param element The element to add
     * @return The evicted element if the buffer was at capacity, null otherwise
     */
    fun add(element: T): T? {
        val evicted = if (buffer.size >= capacity) buffer.removeFirst() else null
        buffer.addLast(element)
        return evicted
    }

    /**
     * Adds multiple elements to the buffer.
     *
     * @param elements The elements to add
     * @return List of evicted elements (may be empty)
     */
    fun addAll(elements: Iterable<T>): List<T> {
        val evicted = mutableListOf<T>()
        for (element in elements) {
            add(element)?.let { evicted.add(it) }
        }
        return evicted
    }

    /**
     * Gets the element at the specified index.
     *
     * Index 0 is the oldest element, index (size - 1) is the newest.
     *
     * @param index The index of the element to retrieve
     * @return The element at the specified index
     * @throws IndexOutOfBoundsException if index is out of range
     */
    operator fun get(index: Int): T {
        if (index < 0 || index >= buffer.size) {
            throw IndexOutOfBoundsException("Index $index out of bounds for size ${buffer.size}")
        }
        return buffer.elementAt(index)
    }

    /**
     * Returns the number of elements in the buffer.
     */
    fun size(): Int = buffer.size

    /**
     * Returns true if the buffer is empty.
     */
    fun isEmpty(): Boolean = buffer.isEmpty()

    /**
     * Returns true if the buffer is at capacity.
     */
    fun isFull(): Boolean = buffer.size >= capacity

    /**
     * Returns the maximum capacity of the buffer.
     */
    fun capacity(): Int = capacity

    /**
     * Clears all elements from the buffer.
     */
    fun clear() {
        buffer.clear()
    }

    /**
     * Returns the oldest element without removing it.
     *
     * @return The oldest element, or null if the buffer is empty
     */
    fun peekFirst(): T? = buffer.peekFirst()

    /**
     * Returns the newest element without removing it.
     *
     * @return The newest element, or null if the buffer is empty
     */
    fun peekLast(): T? = buffer.peekLast()

    /**
     * Returns a sequence over all elements, from oldest to newest.
     */
    fun asSequence(): Sequence<T> = buffer.asSequence()

    /**
     * Returns an iterator over all elements, from oldest to newest.
     */
    operator fun iterator(): Iterator<T> = buffer.iterator()

    /**
     * Returns a list containing all elements, from oldest to newest.
     *
     * Note: This creates a copy of the data.
     */
    fun toList(): List<T> = buffer.toList()

    /**
     * Applies a predicate to each element and returns matching indices.
     *
     * @param predicate The condition to test
     * @return List of indices where predicate returns true
     */
    fun indicesWhere(predicate: (T) -> Boolean): List<Int> {
        val result = mutableListOf<Int>()
        buffer.forEachIndexed { index, element ->
            if (predicate(element)) {
                result.add(index)
            }
        }
        return result
    }

    /**
     * Applies a transform to each element and returns results with indices.
     *
     * @param transform The transformation to apply
     * @return List of pairs (index, transformed value)
     */
    fun <R> mapIndexed(transform: (index: Int, T) -> R): List<R> {
        return buffer.mapIndexed(transform)
    }

    override fun toString(): String = "MessageBuffer(size=${size()}, capacity=$capacity)"
}
