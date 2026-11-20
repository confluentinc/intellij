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
 * Owns the global search bar and keeps it in sync with the per-column filter editors.
 *
 * Input is debounced (200ms) and applied as a composed [RowFilter] on the table's [TableRowSorter].
 * Free-text matching uses [freeTextIndex], so each row check is a single bit lookup.
 * Per-column filters do a case-insensitive substring match on the cell text as displayed,
 * so `timestamp:2026-05` matches the formatted date.
 *
 * Sync works both ways:
 *  - typing `key:foo` in the search bar fills the Key column editor
 *  - typing in a column editor rewrites the search bar text
 * [syncing] stops the two updates from triggering each other.
 */
class SearchBarController(
    parentDisposable: Disposable,
    private val table: JTable,
    private val filterHeader: TableFilterHeader,
    isProducer: Boolean,
    // Star-projected: this class only sets the term and reads the bitset, so the element type is unused.
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
        require(table.model is ListTableModel<*>) { "SearchBarController requires a ListTableModel" }
        Disposer.register(parentDisposable, this)
        searchField.addDocumentListener(searchFieldListener)
        attachEditorListeners()
        unsubscribeRecreated = filterHeader.addControllerRecreatedListener {
            attachEditorListeners()
            // Re-populate fresh editors from existing search text; for edge cases e.g. theme change
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
        // The alarm runs the filter rebuild on the EDT, so the filter is applied once its request has run.
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

        // Rescans the buffer on the EDT, but only when the term changes. Streaming never rescans.
        freeTextIndex.setTerm(parsed.freeText)

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

    // Includes a row if its buffer slot (from `ListTableModel.slotForRow`) is set in [bits].
    private fun slotBitSetFilter(bits: BitSet): RowFilter<TableModel, Int> =
        object : RowFilter<TableModel, Int>() {
            override fun include(entry: Entry<out TableModel, out Int>): Boolean {
                val slot = (table.model as ListTableModel<*>).slotForRow(entry.identifier)
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
    }
}

// Renders a cell as displayed. Shared by the per-column filter and the free-text matcher in `KafkaRecordsOutput`.
internal fun cellDisplayString(columnClass: Class<*>, value: Any?): String =
    if (columnClass == Date::class.java && value is Date) DateRenderer.df.format(value) else value?.toString() ?: ""
