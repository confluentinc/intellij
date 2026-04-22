package io.confluent.intellijplugin.consumer.search

import com.intellij.openapi.Disposable
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.util.Alarm
import io.confluent.intellijplugin.core.table.filters.SearchQueryParser
import io.confluent.intellijplugin.core.table.filters.TableFilterHeader
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import javax.swing.JTable
import javax.swing.RowFilter
import javax.swing.event.DocumentEvent
import javax.swing.table.TableModel
import javax.swing.table.TableRowSorter

/**
 * Owns the global search bar and unifies its filter with the per-column filter editors.
 *
 * Debounces input via [Alarm] (200ms) and applies a composed [RowFilter] to the
 * table's [TableRowSorter]. Filters use [RowFilter.regexFilter] with [Regex.escape] so
 * user input is treated as a literal substring match — this uses [java.util.regex.Matcher.find],
 * which correctly matches within multi-line values (e.g. pretty-printed JSON).
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
) {

    val searchField: SearchTextField = SearchTextField(false).apply {
        textEditor.emptyText.text = KafkaMessagesBundle.message("consumer.search.bar.placeholder")
    }

    private val parser = SearchQueryParser(searchKeyMap(isProducer))
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, parentDisposable)
    private var syncing = false

    init {
        searchField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                if (!syncing) schedule(::onSearchBarChanged)
            }
        })
        filterHeader.columnsController?.forEach { editor ->
            editor?.addListener {
                if (!syncing) schedule(::onColumnEditorChanged)
            }
        }
    }

    private fun schedule(action: () -> Unit) {
        alarm.cancelAllRequests()
        alarm.addRequest(action, DEBOUNCE_MS)
    }

    private fun onSearchBarChanged() {
        val parsed = parser.parse(searchField.text.trim())
        syncing = true
        try {
            filterHeader.columnsController?.forEach { editor ->
                editor?.text = parsed.columnFilters[editor?.modelIndex] ?: ""
            }
        } finally {
            syncing = false
        }
        applyUnifiedFilter(parsed)
    }

    private fun onColumnEditorChanged() {
        val currentFreeText = parser.parse(searchField.text.trim()).freeText
        val columnFilters = mutableMapOf<Int, String>()
        filterHeader.columnsController?.forEach { editor ->
            val text = editor?.text
            if (!text.isNullOrBlank()) {
                columnFilters[editor.modelIndex] = text
            }
        }
        syncing = true
        try {
            searchField.text = parser.buildSearchText(columnFilters, currentFreeText)
        } finally {
            syncing = false
        }
        applyUnifiedFilter(SearchQueryParser.ParsedSearch(columnFilters, currentFreeText))
    }

    private fun applyUnifiedFilter(parsed: SearchQueryParser.ParsedSearch) {
        @Suppress("UNCHECKED_CAST")
        val sorter = table.rowSorter as? TableRowSorter<TableModel> ?: return

        val filters = mutableListOf<RowFilter<TableModel, Int>>()
        for ((modelIndex, value) in parsed.columnFilters) {
            if (value.isNotEmpty()) {
                filters.add(RowFilter.regexFilter("(?i)${Regex.escape(value)}", modelIndex))
            }
        }
        if (parsed.freeText.isNotEmpty()) {
            filters.add(RowFilter.regexFilter("(?i)${Regex.escape(parsed.freeText)}"))
        }
        sorter.rowFilter = when {
            filters.isEmpty() -> null
            filters.size == 1 -> filters[0]
            else -> RowFilter.andFilter(filters)
        }
        table.parent?.repaint()
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
    }
}
