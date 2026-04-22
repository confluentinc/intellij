package io.confluent.intellijplugin.consumer.search

import com.intellij.openapi.Disposable
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.util.Alarm
import io.confluent.intellijplugin.core.table.filters.SearchQueryParser
import io.confluent.intellijplugin.core.table.filters.TableFilterHeader
import io.confluent.intellijplugin.core.table.filters.TableRowFilter
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import javax.swing.JTable
import javax.swing.RowFilter
import javax.swing.event.DocumentEvent
import javax.swing.table.TableModel
import javax.swing.table.TableRowSorter

/**
 * Owns the global search bar and unifies its filter with the per-column filter editors.
 *
 * Debounces input via [Alarm] (200ms) and applies a single composed [RowFilter] to the
 * table's [TableRowSorter]. Column-specific filters route through [TableRowFilter] so
 * numeric operators (`>`, `<`, `=`) remain available. Free-text terms compose via
 * [RowFilter.andFilter] as a case-insensitive match across all columns.
 *
 * Sync is one-directional: typing `key:foo` in the search bar populates the Key column
 * editor. Typing directly in a column editor does NOT write back to the search bar —
 * both inputs contribute to the unified filter independently.
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
    private var syncingFromSearch = false

    init {
        searchField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = schedule(syncFromSearchBar = true)
        })
        filterHeader.columnsController?.forEach { editor ->
            editor?.addListener {
                if (!syncingFromSearch) schedule(syncFromSearchBar = false)
            }
        }
    }

    private fun schedule(syncFromSearchBar: Boolean) {
        alarm.cancelAllRequests()
        alarm.addRequest({ applyFilter(syncFromSearchBar) }, DEBOUNCE_MS)
    }

    private fun applyFilter(syncFromSearchBar: Boolean) {
        val parsed = parser.parse(searchField.text.trim())
        if (syncFromSearchBar) {
            syncingFromSearch = true
            try {
                filterHeader.columnsController?.forEach { editor ->
                    editor?.text = parsed.columnFilters[editor?.modelIndex] ?: ""
                }
            } finally {
                syncingFromSearch = false
            }
        }
        applyUnifiedFilter(parsed.freeText)
    }

    private fun applyUnifiedFilter(freeText: String) {
        @Suppress("UNCHECKED_CAST")
        val sorter = table.rowSorter as? TableRowSorter<TableModel> ?: return

        val columnConditions = mutableListOf<Pair<Int, String>>()
        filterHeader.columnsController?.forEach { editor ->
            val text = editor?.text
            if (!text.isNullOrBlank()) {
                columnConditions.add(editor.modelIndex to text)
            }
        }

        val columnFilter: RowFilter<TableModel, Int>? = if (columnConditions.isNotEmpty()) {
            TableRowFilter(table).apply {
                compareCaseInsensitive = true
                setConditions(columnConditions)
            }
        } else null

        val freeTextFilter: RowFilter<TableModel, Int>? = freeText.takeIf { it.isNotEmpty() }
            ?.let { RowFilter.regexFilter<TableModel, Int>("(?i)${Regex.escape(it)}") }

        sorter.rowFilter = when {
            columnFilter != null && freeTextFilter != null ->
                RowFilter.andFilter(listOf(columnFilter, freeTextFilter))
            columnFilter != null -> columnFilter
            freeTextFilter != null -> freeTextFilter
            else -> null
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
