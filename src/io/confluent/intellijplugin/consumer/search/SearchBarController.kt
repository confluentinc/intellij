package io.confluent.intellijplugin.consumer.search

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.util.Alarm
import io.confluent.intellijplugin.core.table.filters.FilerEditorChangeListener
import io.confluent.intellijplugin.core.table.filters.FilterEditor
import io.confluent.intellijplugin.core.table.filters.SearchQueryParser
import io.confluent.intellijplugin.core.table.filters.TableFilterHeader
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import javax.swing.JTable
import javax.swing.RowFilter
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.TableModel
import javax.swing.table.TableRowSorter
import java.util.concurrent.TimeUnit
import org.jetbrains.annotations.TestOnly

/**
 * Owns the global search bar and unifies its filter with the per-column filter editors.
 *
 * Debounces input via [Alarm] on the Swing thread (200ms) and applies a composed
 * [RowFilter] to the table's [TableRowSorter]. Filters use [RowFilter.regexFilter] with
 * [Regex.escape] so user input is treated as a literal substring match — this uses
 * [java.util.regex.Matcher.find], which correctly matches within multi-line values
 * (e.g. pretty-printed JSON). Debounce matters for large tables (thousands of rows),
 * where re-running the sort+filter on every keystroke causes EDT jank.
 *
 * Sync is bidirectional:
 *  - typing `key:foo` in the search bar populates the Key column editor
 *  - typing in a column editor rebuilds the search bar text to reflect current state
 * A [syncing] flag prevents the two from triggering each other in a loop.
 *
 * Registered with the parent [Disposable] so the alarm and all listeners are cleaned up.
 */
class SearchBarController(
    parentDisposable: Disposable,
    private val table: JTable,
    private val filterHeader: TableFilterHeader,
    isProducer: Boolean,
) : Disposable {

    val searchField: SearchTextField = SearchTextField(false).apply {
        textEditor.emptyText.text = KafkaMessagesBundle.message("consumer.search.bar.placeholder")
    }

    private val parser = SearchQueryParser(searchKeyMap(isProducer))
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private var syncing = false

    private val searchFieldListener: DocumentListener = object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
            if (!syncing) schedule(::onSearchBarChanged)
        }
    }
    private val editorListeners: List<Pair<FilterEditor, FilerEditorChangeListener>>

    init {
        Disposer.register(parentDisposable, this)
        searchField.addDocumentListener(searchFieldListener)
        editorListeners = columnEditors().map { editor ->
            val listener = FilerEditorChangeListener {
                if (!syncing) schedule(::onColumnEditorChanged)
            }
            editor.addListener(listener)
            editor to listener
        }
    }

    override fun dispose() {
        searchField.removeDocumentListener(searchFieldListener)
        editorListeners.forEach { (editor, listener) -> editor.removeListener(listener) }
    }

    @TestOnly
    internal fun waitForPendingInTest() {
        alarm.waitForAllExecuted(1, TimeUnit.SECONDS)
    }

    private fun columnEditors(): List<FilterEditor> =
        filterHeader.columnsController?.filterNotNull().orEmpty()

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
                editor.text = parsed.columnFilters[editor.modelIndex] ?: ""
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
