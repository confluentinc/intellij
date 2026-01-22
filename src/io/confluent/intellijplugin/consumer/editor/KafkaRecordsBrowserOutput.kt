package io.confluent.intellijplugin.consumer.editor

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery
import io.confluent.intellijplugin.common.editor.ListTableModel
import io.confluent.intellijplugin.consumer.editor.webview.*
import io.confluent.intellijplugin.core.ui.ExpansionPanel
import io.confluent.intellijplugin.core.util.invokeLater
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.*
import javax.swing.JPanel
import kotlin.math.max

/**
 * Enhanced JCEF WebView implementation with:
 * - Typed message protocol (WebViewMessages.kt)
 * - Reactive state management (MessageViewerState.kt)
 * - Client-side filtering (SimpleBitSet.kt)
 * - Throttled updates (StateFlow-based)
 *
 * This proves the patterns from VS Code can be adapted to IntelliJ:
 * - ObservableScope → StateFlow
 * - scheduler() → Coroutine throttling
 * - BitSet filtering → SimpleBitSet
 */
class KafkaRecordsBrowserOutput(
    val project: Project,
    val isProducer: Boolean
) : IKafkaRecordsOutput {

    // Keep existing model for backwards compatibility and state persistence
    override val outputModel = ListTableModel(
        LinkedList<KafkaRecord>(),
        listOf(TOPIC_FIELD, TIMESTAMP_FIELD, KEY_COLUMN, VALUE_COLUMN, PARTITION_COLUMN) +
                if (isProducer) listOf(DURATION_COLUMN) else listOf(OFFSET_COLUMN)
    ) { data, index ->
        when (index) {
            0 -> data.topic
            1 -> Date(data.timestamp)
            2 -> data.keyText ?: KafkaMessagesBundle.message("error.output.row.key")
            3 -> data.valueText ?: data.errorText
            4 -> data.partition
            5 -> if (isProducer) data.duration else data.offset
            else -> ""
        }
    }.apply {
        columnClasses = listOf(
            String::class.java,
            Date::class.java,
            String::class.java,
            String::class.java,
            Int::class.java,
            Long::class.java
        )
    }

    private val statisticPanel = ConsumerTableStats()

    // NEW: Reactive state management (equivalent to ObservableScope in VS Code)
    private val viewerState = MessageViewerState()

    // NEW: Coroutine scope for async operations
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // JCEF Browser components
    private val browser = JBCefBrowser()
    private var isHtmlLoaded = false

    // NEW: Enhanced JS Query with typed message handling
    private val jsQuery = JBCefJSQuery.create(browser as com.intellij.ui.jcef.JBCefBrowserBase).also { query ->
        query.addHandler { rawMessage ->
            handleWebViewMessage(rawMessage)
            null
        }
    }

    private val detailsDelegate: Lazy<KafkaRecordDetails> = lazy {
        KafkaRecordDetails(project, this)
    }

    private val details: KafkaRecordDetails by detailsDelegate

    override val dataPanel: ExpansionPanel
    override val detailsPanel: ExpansionPanel

    init {
        // Load HTML content
        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                if (frame?.isMain == true) {
                    isHtmlLoaded = true
                    // Send initial state
                    postStateUpdate()
                }
            }
        }, browser.cefBrowser)

        val html = loadHtmlTemplate()
        browser.loadHTML(html)

        // Create browser panel
        val browserPanel = JPanel(BorderLayout()).apply {
            add(browser.component, BorderLayout.CENTER)
            statisticPanel.toolbar.targetComponent = browser.component
            add(statisticPanel.toolbar.component, BorderLayout.SOUTH)
        }

        val clearButton =
            DumbAwareAction.create(KafkaMessagesBundle.message("action.clear.output"), AllIcons.Actions.GC) {
                outputModel.clear()
                viewerState.clear()
            }

        dataPanel = ExpansionPanel(
            KafkaMessagesBundle.message("toggle.data"), { browserPanel },
            KafkaRecordsOutput.DATA_SHOW_ID, true,
            listOf(ActionManager.getInstance().getAction("Kafka.ExportRecords.Actions"), clearButton)
        )

        detailsPanel = ExpansionPanel(KafkaMessagesBundle.message("toggle.details"), {
            details.component.apply {
                minimumSize = Dimension(max(details.component.minimumSize.width, 250), minimumSize.height)
            }
        }, KafkaRecordsOutput.DETAILS_SHOW_ID, false).apply {
            addChangeListener {
                if (expanded) {
                    updateDetails()
                }
            }
        }

        // NEW: Watch state changes and update UI (like VS Code's watchers)
        setupStateWatchers()
    }

    // NEW: Setup reactive watchers (equivalent to os.watch in VS Code)
    private fun setupStateWatchers() {
        // Watch for UI refresh requests
        viewerState.onUIRefreshNeeded = {
            postRefresh()
        }

        // Watch stream state changes
        scope.launch {
            viewerState.streamState.collectLatest { state ->
                postStateUpdate()
            }
        }

        // Watch for errors
        scope.launch {
            viewerState.errorMessage.collectLatest { error ->
                postStateUpdate()
            }
        }
    }

    override fun dispose() {
        jsQuery.dispose()
        Disposer.dispose(browser)
        Disposer.dispose(viewerState)
        scope.cancel()
    }

    override fun replace(output: List<KafkaRecord>) {
        outputModel.clear()
        output.forEach {
            outputModel.addElement(it)
        }
        viewerState.replaceAll(output)
    }

    override fun stop() {
        executeJavaScript("window.setLoading(false)")
    }

    override fun start() {
        executeJavaScript("window.setLoading(true)")
        statisticPanel.start()
    }

    override fun setMaxRows(limit: Int) {
        outputModel.maxElementsCount = limit
        executeJavaScript("window.setMaxRows($limit)")
    }

    override fun addBatchRows(pollTime: Long, elements: List<KafkaRecord>) {
        // Update model for state management
        elements.forEach {
            outputModel.addElement(it)
        }
        statisticPanel.addRecordsBatch(pollTime, elements)

        // NEW: Update reactive state (triggers watchers)
        viewerState.addBatch(elements)
    }

    override fun addError(element: KafkaRecord) {
        outputModel.addElement(element)
        viewerState.addBatch(listOf(element))
    }

    override fun getElements(): List<KafkaRecord> {
        return outputModel.elements().toList()
    }

    // NEW: Typed message handling (replaces simple string parsing)
    private fun handleWebViewMessage(rawMessage: String): String? {
        val request = MessageSerializer.decodeRequest(rawMessage) ?: return null

        return when (request) {
            is WebViewRequest.GetMessages -> {
                val paged = viewerState.getPagedMessages(request.page, request.pageSize)
                val response = WebViewResponse.Messages(
                    indices = paged.indices,
                    messages = paged.messages.map { it.toDto() }
                )
                MessageSerializer.encodeResponse(response)
            }

            is WebViewRequest.GetMessagesCount -> {
                val count = viewerState.filteredCount.value
                val response = WebViewResponse.MessagesCount(
                    total = count.total,
                    filtered = count.filtered
                )
                MessageSerializer.encodeResponse(response)
            }

            is WebViewRequest.SearchMessages -> {
                viewerState.searchText(request.search)
                null
            }

            is WebViewRequest.PartitionFilterChange -> {
                viewerState.filterByPartitions(request.partitions)
                null
            }

            is WebViewRequest.RowSelected -> {
                invokeLater {
                    updateDetails(request.index)
                }
                null
            }

            is WebViewRequest.StreamPause -> {
                viewerState.pause()
                null
            }

            is WebViewRequest.StreamResume -> {
                viewerState.resume()
                null
            }
        }
    }

    private fun updateDetails(index: Int = -1) {
        if (detailsDelegate.isInitialized()) {
            val row = if (index == -1 || index >= outputModel.rowCount) {
                null
            } else {
                outputModel.getValueAt(index)
            }
            details.update(row)
        }
    }

    private fun executeJavaScript(script: String) {
        if (isHtmlLoaded) {
            browser.cefBrowser.executeJavaScript(script, browser.cefBrowser.url, 0)
        }
    }

    // NEW: Typed message posting to JavaScript
    private fun postToWebView(response: WebViewResponse) {
        val json = MessageSerializer.encodeResponse(response)
        executeJavaScript("window.handleMessage($json)")
    }

    private fun postRefresh() {
        postToWebView(WebViewResponse.Refresh(success = true))
    }

    private fun postStateUpdate() {
        val state = viewerState.streamState.value
        val error = viewerState.errorMessage.value
        postToWebView(
            WebViewResponse.StateUpdate(
                isRunning = state == MessageViewerState.StreamState.RUNNING,
                isLoading = state == MessageViewerState.StreamState.RUNNING,
                errorMessage = error
            )
        )
    }

    private fun KafkaRecord.toDto() = RecordDto(
        topic = topic,
        timestamp = timestamp,
        key = keyText,
        value = valueText ?: errorText,
        partition = partition,
        offset = offset
    )

    private fun loadHtmlTemplate(): String {
        // Try to load from resources, fallback to embedded HTML
        return try {
            javaClass.getResourceAsStream("/html/kafka-consumer-table-enhanced.html")?.bufferedReader()?.readText()
                ?: getEmbeddedHtml()
        } catch (_: Exception) {
            getEmbeddedHtml()
        }
    }

    private fun getEmbeddedHtml(): String {
        val queryInjection = jsQuery.inject("message")
        return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <style>
        * { box-sizing: border-box; }
        body {
            margin: 0;
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            font-size: 13px;
            background: #ffffff;
            overflow: hidden;
        }
        .container {
            display: flex;
            flex-direction: column;
            height: 100vh;
        }
        .toolbar {
            padding: 8px;
            background: #f5f5f5;
            border-bottom: 1px solid #e0e0e0;
            display: flex;
            gap: 8px;
            align-items: center;
        }
        .search-box {
            flex: 1;
            padding: 4px 8px;
            border: 1px solid #ccc;
            border-radius: 3px;
            font-size: 13px;
        }
        .table-container {
            flex: 1;
            overflow: auto;
        }
        table {
            width: 100%;
            border-collapse: collapse;
        }
        thead {
            position: sticky;
            top: 0;
            background: #f5f5f5;
            z-index: 10;
        }
        th {
            padding: 8px;
            text-align: left;
            font-weight: 500;
            border-bottom: 2px solid #e0e0e0;
            white-space: nowrap;
        }
        td {
            padding: 6px 8px;
            border-bottom: 1px solid #f0f0f0;
            max-width: 400px;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }
        tbody tr:hover {
            background: #f7f7f7;
        }
        tbody tr.selected {
            background: #cce5ff !important;
        }
        .status-bar {
            padding: 4px 8px;
            background: #f5f5f5;
            border-top: 1px solid #e0e0e0;
            font-size: 12px;
            color: #666;
        }
        .loading {
            padding: 20px;
            text-align: center;
            color: #666;
        }
        .error {
            padding: 12px;
            background: #fff3cd;
            border: 1px solid #ffc107;
            color: #856404;
            margin: 8px;
            border-radius: 3px;
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="toolbar">
            <input type="text" class="search-box" id="searchBox" placeholder="Search key or value...">
            <button onclick="clearSearch()">Clear</button>
        </div>

        <div id="error" class="error" style="display: none;"></div>

        <div class="table-container">
            <table>
                <thead>
                    <tr>
                        <th>Topic</th>
                        <th>Timestamp</th>
                        <th>Key</th>
                        <th>Value</th>
                        <th>Partition</th>
                        <th>Offset</th>
                    </tr>
                </thead>
                <tbody id="tableBody"></tbody>
            </table>
            <div id="loading" class="loading" style="display: none;">Loading messages...</div>
        </div>

        <div class="status-bar">
            <span id="status">Ready</span>
        </div>
    </div>

    <script>
        // State
        let currentPage = 0;
        let pageSize = 100;
        let selectedIndex = -1;
        let totalMessages = 0;
        let filteredMessages = null;
        let searchTimeout = null;

        // Send message to Kotlin
        function sendMessage(type, payload) {
            const message = JSON.stringify({ type, payload: JSON.stringify(payload) });
            $queryInjection
        }

        // Handle messages from Kotlin
        window.handleMessage = function(messageJson) {
            const message = JSON.parse(messageJson);
            const payload = JSON.parse(message.payload);

            switch (message.type) {
                case 'Messages':
                    renderMessages(payload);
                    break;
                case 'MessagesCount':
                    updateStatus(payload);
                    break;
                case 'Refresh':
                    refresh();
                    break;
                case 'StateUpdate':
                    updateState(payload);
                    break;
            }
        };

        // Search box handler with debouncing
        document.getElementById('searchBox').addEventListener('input', function(e) {
            clearTimeout(searchTimeout);
            searchTimeout = setTimeout(() => {
                const query = e.target.value.trim();
                sendMessage('SearchMessages', { search: query || null });
                currentPage = 0;
            }, 300);
        });

        function clearSearch() {
            document.getElementById('searchBox').value = '';
            sendMessage('SearchMessages', { search: null });
            currentPage = 0;
        }

        function refresh() {
            sendMessage('GetMessages', { page: currentPage, pageSize });
            sendMessage('GetMessagesCount', {});
        }

        function renderMessages(data) {
            const tbody = document.getElementById('tableBody');
            tbody.innerHTML = '';

            if (!data.messages || data.messages.length === 0) {
                tbody.innerHTML = '<tr><td colspan="6" style="text-align: center; padding: 20px; color: #999;">No messages</td></tr>';
                return;
            }

            data.messages.forEach((record, idx) => {
                const row = tbody.insertRow();
                row.className = data.indices[idx] === selectedIndex ? 'selected' : '';
                row.onclick = () => selectRow(data.indices[idx]);

                row.insertCell().textContent = record.topic;
                row.insertCell().textContent = formatTimestamp(record.timestamp);
                row.insertCell().textContent = record.key || '(null)';
                row.insertCell().textContent = record.value || '(null)';
                row.insertCell().textContent = record.partition;
                row.insertCell().textContent = record.offset;
            });
        }

        function updateStatus(data) {
            totalMessages = data.total;
            filteredMessages = data.filtered;

            const status = document.getElementById('status');
            if (filteredMessages !== null && filteredMessages !== totalMessages) {
                status.textContent = `Showing ${'$'}{filteredMessages} of ${'$'}{totalMessages} messages`;
            } else {
                status.textContent = `${'$'}{totalMessages} messages`;
            }
        }

        function updateState(state) {
            const loading = document.getElementById('loading');
            loading.style.display = state.isLoading ? 'block' : 'none';

            const error = document.getElementById('error');
            if (state.errorMessage) {
                error.textContent = state.errorMessage;
                error.style.display = 'block';
            } else {
                error.style.display = 'none';
            }
        }

        function formatTimestamp(timestamp) {
            const date = new Date(timestamp);
            return date.toLocaleString();
        }

        function selectRow(index) {
            selectedIndex = index;
            sendMessage('RowSelected', { index });
            refresh();
        }

        window.setLoading = function(isLoading) {
            document.getElementById('loading').style.display = isLoading ? 'block' : 'none';
        };

        window.setMaxRows = function(limit) {
            pageSize = Math.min(100, limit);
        };

        // Initial load
        refresh();

        // Auto-refresh on data changes
        setInterval(() => {
            if (document.visibilityState === 'visible') {
                sendMessage('GetMessagesCount', {});
            }
        }, 1000);
    </script>
</body>
</html>
        """.trimIndent()
    }

    companion object {
        private val TOPIC_FIELD = KafkaMessagesBundle.message("output.column.topic")
        private val TIMESTAMP_FIELD = KafkaMessagesBundle.message("output.column.timestamp")
        private val KEY_COLUMN = KafkaMessagesBundle.message("output.column.key")
        private val VALUE_COLUMN = KafkaMessagesBundle.message("output.column.value")
        private val PARTITION_COLUMN = KafkaMessagesBundle.message("output.column.partition")
        private val OFFSET_COLUMN = KafkaMessagesBundle.message("output.column.offset")
        private val DURATION_COLUMN = KafkaMessagesBundle.message("output.column.duration")
    }
}
