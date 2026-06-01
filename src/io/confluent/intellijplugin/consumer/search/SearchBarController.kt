package io.confluent.intellijplugin.consumer.search

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.util.Alarm
import io.confluent.intellijplugin.common.editor.ListTableModel
import io.confluent.intellijplugin.consumer.data.FreeTextSlotIndex
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
 * to the table's [TableRowSorter]. Free-text matching is delegated to [freeTextIndex], an
 * incrementally-maintained slot-keyed [BitSet]: the term-change rescan is one-time, and the bits
 * stay live as records stream in, so per-row visibility is O(1) at repaint time. Per-column filters
 * use case-insensitive [String.contains] on the cell's string value (matched against the rendered
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
    private val freeTextIndex: FreeTextSlotIndex<*>,
) : Disposable {

    val searchField: SearchTextField = SearchTextField(false).apply {
        textEditor.emptyText.text = KafkaMessagesBundle.message("consumer.search.placeholder")
    }

    private val parser = SearchQueryParser(searchKeyMap(isProducer))
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private var syncing = false
    private var lastApplied: SearchQueryParser.ParsedSearch? = null

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
    internal fun waitForPendingInTest() {
        // The debounce alarm runs the filter rebuild synchronously on the EDT, so once its queued
        // request has executed the composed RowFilter is fully applied — no further round-trip.
        alarm.waitForAllExecuted(1, TimeUnit.SECONDS)
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

        // One-time rescan when the term changes; the index keeps the bits live as records stream in.
        // The rescan runs on the EDT (debounced via [alarm]); steady-state streaming never rescans,
        // so this is the only term-bound EDT work — acceptable at the current record cap.
        freeTextIndex.setTerm(parsed.freeText)
        assembleAndApply(sorter, parsed)
    }

    private fun assembleAndApply(sorter: TableRowSorter<TableModel>, parsed: SearchQueryParser.ParsedSearch) {
        val filters = mutableListOf<RowFilter<TableModel, Int>>()
        for ((modelIndex, value) in parsed.columnFilters) {
            if (value.isNotEmpty()) {
                filters.add(columnContainsFilter(value, modelIndex))
            }
        }
        val bits = freeTextIndex.bitSet()
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

    /**
     * Slot-keyed RowFilter: translates the row-typed entry identifier to its current buffer slot
     * and consults [freeTextIndex]'s live BitSet. The translation comes from the table model — when
     * the model is a `ListTableModel`, it surfaces `slotForRow`; otherwise we fall back to the row
     * index (correct for non-circular models like `DefaultTableModel`).
     */
    private fun slotBitSetFilter(bits: BitSet): RowFilter<TableModel, Int> =
        object : RowFilter<TableModel, Int>() {
            override fun include(entry: Entry<out TableModel, out Int>): Boolean {
                val slot = slotForRow(table.model, entry.identifier)
                return bits[slot]
            }
        }

    private fun columnContainsFilter(needle: String, modelIndex: Int): RowFilter<TableModel, Int> =
        object : RowFilter<TableModel, Int>() {
            override fun include(entry: Entry<out TableModel, out Int>): Boolean =
                cellAsDisplayedString(entry, modelIndex).contains(needle, ignoreCase = true)
        }

    // Match against what the user sees in the cell, not Object.toString() — see [cellDisplayString].
    private fun cellAsDisplayedString(entry: RowFilter.Entry<out TableModel, out Int>, columnIndex: Int): String =
        cellDisplayString(entry.model.getColumnClass(columnIndex), entry.getValue(columnIndex))

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
    }
}

// Renders a cell as the user sees it, not Object.toString(). The Timestamp column is the
// load-bearing case: Date.toString() is "Fri May 01 ... 2026" but the renderer shows
// "2026-05-01 14:23:45", so without this, typing "-" or "05" against the visible date drops every
// row. Shared by the per-column RowFilter and the free-text matcher in `KafkaRecordsOutput` so both
// match the same rendered surface.
internal fun cellDisplayString(columnClass: Class<*>, value: Any?): String =
    if (columnClass == Date::class.java && value is Date) DateRenderer.df.format(value) else value?.toString() ?: ""
