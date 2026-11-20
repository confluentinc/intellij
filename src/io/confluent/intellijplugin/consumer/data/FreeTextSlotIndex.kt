package io.confluent.intellijplugin.consumer.data

import com.intellij.util.concurrency.annotations.RequiresEdt
import java.util.BitSet

/**
 * Free-text search index: one bit per buffer slot, set when that record matches the search term.
 *
 * The whole buffer is scanned only when the term changes; after that, [onAppend] and [onEvict]
 * update a single bit per record. Bits are keyed by slot rather than row so they stay valid when
 * the [CircularBuffer] wraps.
 *
 * **Threading:** EDT only, like [ConsumerRecordIndex].
 *
 * @param matcher whether an element matches the term.
 * @param slotElements the current `(slot, element)` pairs, used to rescan in [setTerm].
 */
class FreeTextSlotIndex<T : Any>(
    private val capacity: Int,
    private val matcher: (element: T, term: String) -> Boolean,
    private val slotElements: () -> Sequence<Pair<Int, T>>,
) {
    private var term: String = ""

    /** Slot-keyed BitSet for the active term, or `null` when no term is active. */
    var bitSet: BitSet? = null
        private set

    /**
     * Set the active term and rebuild the bitset from the current elements. A blank term clears the
     * index ([bitSet] becomes `null`).
     */
    @RequiresEdt
    fun setTerm(term: String) {
        // Unchanged term: the bits are already current
        if (term == this.term) return
        this.term = term
        // The rescan covers only flushed elements. Records still in the model's pending-add queue are
        // intentionally skipped since they set their own bit via [onAppend] when they flush
        bitSet = if (term.isEmpty()) null else BitSet(capacity).apply {
            for ((slot, element) in slotElements()) if (matcher(element, term)) set(slot)
        }
    }

    /** Update [slot]'s bit for a freshly appended (or wrap-reused) element. No-op when inactive. */
    @RequiresEdt
    fun onAppend(slot: Int, element: T) {
        val current = bitSet ?: return
        if (matcher(element, term)) current.set(slot) else current.clear(slot)
    }

    /** Clear the bit for a slot that has been freed for good. */
    @RequiresEdt
    fun onEvict(slot: Int) {
        bitSet?.clear(slot)
    }

    /** Drop all live bits (the buffer was cleared) while keeping the term active. */
    @RequiresEdt
    fun onClear() {
        bitSet?.clear()
    }
}
