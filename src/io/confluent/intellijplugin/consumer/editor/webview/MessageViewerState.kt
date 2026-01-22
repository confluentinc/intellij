package io.confluent.intellijplugin.consumer.editor.webview

import com.intellij.openapi.Disposable
import io.confluent.intellijplugin.consumer.editor.KafkaRecord
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Reactive state management for Message Viewer.
 * Uses Kotlin StateFlow as equivalent to VS Code's ObservableScope.
 *
 * Pattern mapping:
 * - os.signal<T>(value) → MutableStateFlow<T>(value)
 * - os.derive(() => ...) → combine() or stateIn()
 * - os.watch((signal) => ...) → flow.collect { ... }
 * - os.batch(() => ...) → coroutineScope { ... }
 */
class MessageViewerState(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : Disposable {

    // Signals (reactive state)
    private val _streamState = MutableStateFlow(StreamState.RUNNING)
    val streamState: StateFlow<StreamState> = _streamState.asStateFlow()

    private val _records = MutableStateFlow<List<KafkaRecord>>(emptyList())
    val records: StateFlow<List<KafkaRecord>> = _records.asStateFlow()

    private val _textFilter = MutableStateFlow<TextFilterState?>(null)
    val textFilter: StateFlow<TextFilterState?> = _textFilter.asStateFlow()

    private val _partitionFilter = MutableStateFlow<List<Int>?>(null)
    val partitionFilter: StateFlow<List<Int>?> = _partitionFilter.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Rate limiting for updates (similar to VS Code's scheduler)
    private val updateThrottle = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val batchCounter = AtomicInteger(0)

    // Derived state: combined filter bitset (like VS Code's bitset in consume.ts:334-344)
    val filterBitSet: StateFlow<SimpleBitSet?> = combine(
        _textFilter,
        _partitionFilter,
        _records
    ) { textFilter, partitionFilter, records ->
        if (records.isEmpty()) return@combine null

        val capacity = records.size
        var result: SimpleBitSet? = null

        // Apply text filter
        textFilter?.let { filter ->
            result = filter.bitset.copy()
        }

        // Apply partition filter
        partitionFilter?.let { partitions ->
            val bitset = SimpleBitSet(capacity)
            records.forEachIndexed { index, record ->
                if (partitions.contains(record.partition)) {
                    bitset.set(index)
                }
            }
            result = result?.intersection(bitset) ?: bitset
        }

        result
    }.stateIn(scope, SharingStarted.Eagerly, null)

    // Derived: filtered count (like VS Code's GetMessagesCount handler)
    val filteredCount: StateFlow<FilteredCount> = combine(
        _records,
        filterBitSet
    ) { records, bitset ->
        FilteredCount(
            total = records.size,
            filtered = bitset?.count()
        )
    }.stateIn(scope, SharingStarted.Eagerly, FilteredCount(0, null))

    init {
        // Watcher: throttle UI updates (similar to VS Code's notifyUI pattern)
        scope.launch {
            updateThrottle
                .debounce(50) // 50ms debounce
                .collect {
                    // Trigger UI refresh
                    onUIRefreshNeeded?.invoke()
                }
        }
    }

    // Callback for UI updates
    var onUIRefreshNeeded: (() -> Unit)? = null

    // Actions (like VS Code's processMessage handlers)

    fun addBatch(newRecords: List<KafkaRecord>) {
        scope.launch {
            _records.update { current ->
                val combined = current + newRecords
                // TODO: Apply max capacity limit
                combined
            }

            // Update text filter bitset if active
            _textFilter.value?.let { filter ->
                val startIndex = _records.value.size - newRecords.size
                newRecords.forEachIndexed { offset, record ->
                    val index = startIndex + offset
                    if (matchesTextFilter(record, filter.regex)) {
                        filter.bitset.set(index)
                    }
                }
            }

            requestUIUpdate()
        }
    }

    fun replaceAll(newRecords: List<KafkaRecord>) {
        scope.launch {
            _records.value = newRecords
            // Clear filters
            _textFilter.value = null
            _partitionFilter.value = null
            requestUIUpdate()
        }
    }

    fun clear() {
        scope.launch {
            _records.value = emptyList()
            _textFilter.value = null
            _partitionFilter.value = null
            requestUIUpdate()
        }
    }

    fun searchText(query: String?) {
        scope.launch {
            if (query.isNullOrBlank()) {
                _textFilter.value = null
            } else {
                val records = _records.value
                val bitset = SimpleBitSet(records.size)
                val regex = createSearchRegex(query)

                records.forEachIndexed { index, record ->
                    if (matchesTextFilter(record, regex)) {
                        bitset.set(index)
                    }
                }

                _textFilter.value = TextFilterState(
                    query = query,
                    regex = regex,
                    bitset = bitset
                )
            }
            requestUIUpdate()
        }
    }

    fun filterByPartitions(partitions: List<Int>?) {
        scope.launch {
            _partitionFilter.value = partitions
            requestUIUpdate()
        }
    }

    fun pause() {
        _streamState.value = StreamState.PAUSED
        requestUIUpdate()
    }

    fun resume() {
        _streamState.value = StreamState.RUNNING
        requestUIUpdate()
    }

    fun setError(message: String?) {
        _errorMessage.value = message
        requestUIUpdate()
    }

    // Get paginated, filtered messages (like VS Code's GetMessages handler)
    fun getPagedMessages(page: Int, pageSize: Int): PagedMessages {
        val allRecords = _records.value
        val bitset = filterBitSet.value
        val predicate = bitset?.predicate() ?: { true }

        val offset = page * pageSize
        val results = mutableListOf<KafkaRecord>()
        val indices = mutableListOf<Int>()

        var count = 0
        for (i in allRecords.indices) {
            if (predicate(i)) {
                if (count >= offset && count < offset + pageSize) {
                    results.add(allRecords[i])
                    indices.add(i)
                }
                count++
                if (count >= offset + pageSize) break
            }
        }

        return PagedMessages(
            indices = indices,
            messages = results
        )
    }

    private fun requestUIUpdate() {
        updateThrottle.tryEmit(Unit)
    }

    private fun createSearchRegex(query: String): Regex {
        // Escape special regex chars and make whitespace optional (like VS Code)
        val escaped = query
            .replace(Regex("[.*+?^${'$'}{}()|\\[\\]\\\\]")) { "\\\\" + it.value }
            .replace(Regex("\\s+|\\b"), "\\\\s*")
        return Regex(escaped, RegexOption.IGNORE_CASE)
    }

    private fun matchesTextFilter(record: KafkaRecord, regex: Regex): Boolean {
        val key = record.keyText ?: ""
        val value = record.valueText ?: record.errorText
        return regex.containsMatchIn(key) || regex.containsMatchIn(value)
    }

    override fun dispose() {
        scope.cancel()
    }

    enum class StreamState {
        RUNNING,
        PAUSED
    }

    data class TextFilterState(
        val query: String,
        val regex: Regex,
        val bitset: SimpleBitSet
    )

    data class FilteredCount(
        val total: Int,
        val filtered: Int?
    )

    data class PagedMessages(
        val indices: List<Int>,
        val messages: List<KafkaRecord>
    )
}
