package io.confluent.intellijplugin.consumer.data

import java.util.BitSet

/**
 * Incrementally-maintained, slot-keyed free-text search index.
 *
 * Mirrors the Confluent Extension for VS Code's `Stream` search design: a single full scan when the
 * search term changes, then O(1) per-record maintenance as the stream grows. [onAppend] / [onEvict]
 * flip a single slot bit so the active filter stays live during consumption instead of going stale
 * between term changes.
 *
 * Slot-keyed (not row-keyed) so the bits survive [CircularBuffer] wrap: a record's match state is
 * tied to its physical buffer slot, which is stable until the slot is overwritten.
 *
 * **Threading:** all access must be serialized on a single thread (the EDT, in the message-viewer
 * wiring), matching [ConsumerRecordIndex]. The append/evict hooks run inside the table model's
 * slot-change dispatch, which is already EDT-confined.
 *
 * @param matcher decides whether an element matches the active term; called once per record on
 *   append and once per live record on [setTerm].
 * @param slotElements supplies the current `(slot, element)` pairs for a full rescan on [setTerm].
 */
class FreeTextSlotIndex<T : Any>(
    private val capacity: Int,
    private val matcher: (element: T, term: String) -> Boolean,
    private val slotElements: () -> Sequence<Pair<Int, T>>,
) {
    private var term: String = ""
    private var bits: BitSet? = null

    /** Slot-keyed BitSet for the active term, or `null` when no term is active. */
    fun bitSet(): BitSet? = bits

    /**
     * Set the active term and rebuild the bitset from the current elements. A blank term clears the
     * index (subsequent [bitSet] returns `null`).
     */
    fun setTerm(term: String) {
        // Unchanged term: the bits are already current (rebuilt once, then maintained incrementally),
        // so a column-filter-only change must not trigger a wasteful rescan.
        if (term == this.term) return
        if (term.isEmpty()) {
            this.term = ""
            bits = null
            return
        }
        this.term = term
        val rebuilt = BitSet(capacity)
        for ((slot, element) in slotElements()) {
            if (matcher(element, term)) rebuilt.set(slot)
        }
        bits = rebuilt
    }

    /** Update [slot]'s bit for a freshly appended (or wrap-reused) element. No-op when inactive. */
    fun onAppend(slot: Int, element: T) {
        val current = bits ?: return
        if (matcher(element, term)) current.set(slot) else current.clear(slot)
    }

    /** Clear the bit for a slot that has been freed for good. */
    fun onEvict(slot: Int) {
        bits?.clear(slot)
    }

    /** Drop all live bits (the buffer was cleared) while keeping the term active. */
    fun onClear() {
        bits?.clear()
    }
}
