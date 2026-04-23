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
 * Debounces input via [Alarm] on the Swing thread (300ms) and applies a composed
 * [RowFilter] to the table's [TableRowSorter]. Filters use case-insensitive
 * [String.contains] on each cell's string value. Debounce matters for large
 * tables (thousands of rows), where re-running the sort+filter on every keystroke causes
 * EDT jank.
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

    val searchField: SearchTextField = SearchTextField(false).apply {
        textEditor.emptyText.text = KafkaMessagesBundle.message("consumer.search.bar.placeholder")
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

    init {
        Disposer.register(parentDisposable, this)
        searchField.addDocumentListener(searchFieldListener)
        attachEditorListeners()
        filterHeader.addControllerRecreatedListener {
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
    }

    @TestOnly
    internal fun waitForPendingInTest() {
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

        val filters = mutableListOf<RowFilter<TableModel, Int>>()
        for ((modelIndex, value) in parsed.columnFilters) {
            if (value.isNotEmpty()) {
                filters.add(containsFilter(value, modelIndex))
            }
        }
        if (parsed.freeText.isNotEmpty()) {
            filters.add(containsFilter(parsed.freeText, null))
        }
        sorter.rowFilter = when {
            filters.isEmpty() -> null
            filters.size == 1 -> filters[0]
            else -> RowFilter.andFilter(filters)
        }
        table.parent?.repaint()
    }

    /**
     * Literal case-insensitive substring filter.
     * Passing `null` for [modelIndex] matches across every column.
     */
    private fun containsFilter(needle: String, modelIndex: Int?): RowFilter<TableModel, Int> =
        object : RowFilter<TableModel, Int>() {
            override fun include(entry: Entry<out TableModel, out Int>): Boolean {
                if (modelIndex != null) {
                    return entry.getStringValue(modelIndex).contains(needle, ignoreCase = true)
                }
                for (i in 0 until entry.valueCount) {
                    if (entry.getStringValue(i).contains(needle, ignoreCase = true)) return true
                }
                return false
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
    }
}
