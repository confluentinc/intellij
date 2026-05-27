package io.confluent.intellijplugin.consumer.search

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.util.Disposer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.util.Alarm
import io.confluent.intellijplugin.common.editor.ListTableModel
import io.confluent.intellijplugin.core.table.filters.FilerEditorChangeListener
import io.confluent.intellijplugin.core.table.filters.FilterEditor
import io.confluent.intellijplugin.core.table.filters.SearchQueryParser
import io.confluent.intellijplugin.core.table.filters.TableFilterHeader
import io.confluent.intellijplugin.core.table.renderers.DateRenderer
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import java.util.BitSet
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.swing.JTable
import javax.swing.RowFilter
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.TableModel
import javax.swing.table.TableRowSorter
import org.jetbrains.annotations.TestOnly

/**
 * Owns the global search bar and unifies its filter with the per-column filter editors.
 *
 * Debounces input via [Alarm] on the Swing thread (200ms) and applies a composed [RowFilter]
 * to the table's [TableRowSorter]. Free-text matches are computed off the EDT into a
 * slot-keyed [BitSet] (so per-row visibility is O(1) at repaint time); per-column filters use
 * case-insensitive [String.contains] on the cell's string value (matched against the rendered
 * form via [cellAsDisplayedString], so e.g. typing `timestamp:2026-05` hits the formatted date).
 *
 * Sync is bidirectional:
 *  - typing `key:foo` in the search bar populates the Key column editor
 *  - typing in a column editor rebuilds the search bar text to reflect current state
 * A [syncing] flag prevents the two from triggering each other in a loop.
 */
class SearchBarController(
    parentDisposable: Disposable,
    private val table: JTable,
    private val filterHeader: TableFilterHeader,
    isProducer: Boolean,
) : Disposable {

    private data class SearchableSlot(val slot: Int, val text: String)

    val searchField: SearchTextField = SearchTextField(false).apply {
        textEditor.emptyText.text = KafkaMessagesBundle.message("consumer.search.placeholder")
    }

    private val parser = SearchQueryParser(searchKeyMap(isProducer))
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private var syncing = false
    private var lastApplied: SearchQueryParser.ParsedSearch? = null

    /**
     * Cached BitSet for the most recent free-text term (slot-keyed). `null` means "no free text".
     * Written on the EDT inside the pool job's invokeLater callback; read on the EDT during
     * `applyUnifiedFilter` and during the RowSorter's filter evaluation. Tests read it via
     * [freeTextBitSetForTest] only after `waitForPendingInTest` synchronizes through invokeAndWait.
     */
    private var freeTextBitSet: BitSet? = null

    /** The free-text term that produced [freeTextBitSet]; used to skip rebuilds when unchanged. */
    private var freeTextSnapshot: String = ""

    /**
     * Tracks the most recent off-EDT BitSet build so tests can deterministically wait for it.
     * Read by [waitForPendingInTest] from the test thread, so the volatile publication matters.
     */
    @Volatile
    private var pendingFreeTextBuild: java.util.concurrent.Future<*>? = null

    private val searchFieldListener: DocumentListener = object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
            if (!syncing) schedule(::onSearchBarChanged)
        }
    }
    private var editorListeners: List<Pair<FilterEditor, FilerEditorChangeListener>> = emptyList()
    private val unsubscribeRecreated: () -> Unit

    init {
        Disposer.register(parentDisposable, this)
        searchField.addDocumentListener(searchFieldListener)
        attachEditorListeners()
        unsubscribeRecreated = filterHeader.addControllerRecreatedListener {
            attachEditorListeners()
            // New editors start blank; repopulate from the current search text so they
            // reflect active `col:value` tokens instead of appearing empty until the user
            // types again.
            if (searchField.text.isNotEmpty()) onSearchBarChanged()
        }
    }

    private fun attachEditorListeners() {
        detachEditorListeners()
        editorListeners = columnEditors().map { editor ->
            val listener = FilerEditorChangeListener {
                if (!syncing) schedule(::onColumnEditorChanged)
            }
            editor.addListener(listener)
            editor to listener
        }
    }

    private fun detachEditorListeners() {
        editorListeners.forEach { (editor, listener) -> editor.removeListener(listener) }
        editorListeners = emptyList()
    }

    override fun dispose() {
        searchField.removeDocumentListener(searchFieldListener)
        detachEditorListeners()
        unsubscribeRecreated()
    }

    @TestOnly
    internal fun freeTextBitSetForTest(): BitSet? = freeTextBitSet

    @TestOnly
    internal fun freeTextSnapshotForTest(): String = freeTextSnapshot

    @TestOnly
    internal fun waitForPendingInTest() {
        alarm.waitForAllExecuted(1, TimeUnit.SECONDS)
        // The alarm callback may have dispatched a pooled-thread BitSet build that finishes by
        // posting back to the EDT. Wait for the pool job and then drain the EDT once to apply it.
        pendingFreeTextBuild?.get(1, TimeUnit.SECONDS)
        ApplicationManager.getApplication().invokeAndWait { /* drain pending invokeLater */ }
    }

    private fun columnEditors(): List<FilterEditor> =
        filterHeader.columnsController?.toList().orEmpty()

    private inline fun withSyncing(block: () -> Unit) {
        syncing = true
        try {
            block()
        } finally {
            syncing = false
        }
    }

    private fun schedule(action: () -> Unit) {
        alarm.cancelAllRequests()
        alarm.addRequest(action, DEBOUNCE_MS)
    }

    private fun onSearchBarChanged() {
        val parsed = parser.parse(searchField.text.trim())
        withSyncing {
            columnEditors().forEach { editor ->
                val target = parsed.columnFilters[editor.modelIndex] ?: ""
                if ((editor.text ?: "") != target) {
                    editor.text = target
                }
            }
        }
        applyUnifiedFilter(parsed)
    }

    private fun onColumnEditorChanged() {
        val currentFreeText = parser.parse(searchField.text.trim()).freeText
        val columnFilters = mutableMapOf<Int, String>()
        columnEditors().forEach { editor ->
            val text = editor.text
            if (!text.isNullOrBlank()) {
                columnFilters[editor.modelIndex] = text
            }
        }
        withSyncing {
            searchField.text = parser.buildSearchText(columnFilters, currentFreeText)
        }
        applyUnifiedFilter(SearchQueryParser.ParsedSearch(columnFilters, currentFreeText))
    }

    private fun applyUnifiedFilter(parsed: SearchQueryParser.ParsedSearch) {
        @Suppress("UNCHECKED_CAST")
        val sorter = table.rowSorter as? TableRowSorter<TableModel> ?: return

        if (parsed == lastApplied) return
        lastApplied = parsed

        val freeText = parsed.freeText
        if (freeText.isEmpty()) {
            freeTextBitSet = null
            freeTextSnapshot = ""
            assembleAndApply(sorter, parsed)
        } else if (freeText == freeTextSnapshot && freeTextBitSet != null) {
            // Same term as last build — reuse the cached BitSet immediately.
            assembleAndApply(sorter, parsed)
        } else {
            // Build off-EDT, then flip the row filter on EDT. Cancellation is best-effort: the
            // tight String.contains loop ignores interrupts, so a build that has already started
            // will run to completion. The real stale-result guard is the lastApplied check inside
            // the invokeLater callback below — cancel() only saves work for futures still queued.
            pendingFreeTextBuild?.cancel(true)
            val snapshot = snapshotRows(table)
            val term = freeText
            pendingFreeTextBuild = ApplicationManager.getApplication().executeOnPooledThread {
                val bits = buildFreeTextBitSet(snapshot, term)
                invokeLater {
                    // Stale-result guard: another keystroke might have advanced lastApplied.
                    if (lastApplied?.freeText != term) return@invokeLater
                    freeTextSnapshot = term
                    freeTextBitSet = bits
                    assembleAndApply(sorter, parsed)
                }
            }
        }
    }

    private fun assembleAndApply(sorter: TableRowSorter<TableModel>, parsed: SearchQueryParser.ParsedSearch) {
        val filters = mutableListOf<RowFilter<TableModel, Int>>()
        for ((modelIndex, value) in parsed.columnFilters) {
            if (value.isNotEmpty()) {
                filters.add(columnContainsFilter(value, modelIndex))
            }
        }
        val bits = freeTextBitSet
        if (bits != null) {
            filters.add(slotBitSetFilter(bits))
        }
        sorter.rowFilter = when {
            filters.isEmpty() -> null
            filters.size == 1 -> filters[0]
            else -> RowFilter.andFilter(filters)
        }
        table.parent?.repaint()
    }

    private fun buildFreeTextBitSet(slots: List<SearchableSlot>, term: String): BitSet {
        val bits = BitSet()
        for ((slot, text) in slots) {
            if (text.contains(term, ignoreCase = true)) bits.set(slot)
        }
        return bits
    }

    /**
     * Slot-keyed RowFilter: translates the row-typed entry identifier to its current buffer slot
     * and consults the cached BitSet. The translation comes from the table model — when the
     * model is a `ListTableModel`, it surfaces `slotForRow`; otherwise we fall back to the row
     * index (correct for non-circular models like `DefaultTableModel`).
     */
    private fun slotBitSetFilter(bits: BitSet): RowFilter<TableModel, Int> =
        object : RowFilter<TableModel, Int>() {
            override fun include(entry: Entry<out TableModel, out Int>): Boolean {
                val row = entry.identifier as? Int ?: return true
                val slot = slotForRow(table.model, row)
                return bits.get(slot)
            }
        }

    private fun columnContainsFilter(needle: String, modelIndex: Int): RowFilter<TableModel, Int> =
        object : RowFilter<TableModel, Int>() {
            override fun include(entry: Entry<out TableModel, out Int>): Boolean =
                cellAsDisplayedString(entry, modelIndex).contains(needle, ignoreCase = true)
        }

    // Match against what the user sees in the cell, not Object.toString(). The Timestamp column
    // is the load-bearing case: Date.toString() is "Fri May 01 ... 2026" but the renderer shows
    // "2026-05-01 14:23:45", so without this typing "-" or "05" against the visible date drops
    // every row.
    private fun cellAsDisplayedString(entry: RowFilter.Entry<out TableModel, out Int>, columnIndex: Int): String {
        val value = entry.getValue(columnIndex) ?: return ""
        return if (entry.model.getColumnClass(columnIndex) == Date::class.java && value is Date) {
            DateRenderer.df.format(value)
        } else {
            value.toString()
        }
    }

    companion object {
        private const val DEBOUNCE_MS = 200

        internal fun searchKeyMap(isProducer: Boolean): Map<String, Int> = buildMap {
            put("topic", 0)
            put("timestamp", 1)
            put("key", 2)
            put("value", 3)
            put("partition", 4)
            put(if (isProducer) "duration" else "offset", 5)
        }

        private fun slotForRow(model: TableModel, row: Int): Int =
            (model as? ListTableModel<*>)?.slotForRow(row) ?: row

        // NUL separator between column strings so the haystack can't accidentally span a column
        // boundary (e.g. key="foo" + value="bar" must not match free-text "oo b"). Safe to use
        // as a separator because the search field cannot produce a literal NUL in the needle.
        private const val COLUMN_SEPARATOR: String = "\u0000"

        private fun snapshotRows(table: JTable): List<SearchableSlot> {
            val model = table.model
            val rowCount = model.rowCount
            val out = ArrayList<SearchableSlot>(rowCount)
            for (row in 0 until rowCount) {
                val builder = StringBuilder()
                for (col in 0 until model.columnCount) {
                    builder.append(cellAsDisplayedString(model, row, col))
                    builder.append(COLUMN_SEPARATOR)
                }
                out.add(SearchableSlot(slotForRow(model, row), builder.toString()))
            }
            return out
        }

        // Mirrors the per-column filter's matching surface: render Date columns via DateRenderer
        // so free-text "-" / "05" / "2026-06-15" hit the same string the user sees.
        private fun cellAsDisplayedString(model: TableModel, row: Int, col: Int): String {
            val value = model.getValueAt(row, col) ?: return ""
            return if (model.getColumnClass(col) == Date::class.java && value is Date) {
                DateRenderer.df.format(value)
            } else {
                value.toString()
            }
        }
    }
}
