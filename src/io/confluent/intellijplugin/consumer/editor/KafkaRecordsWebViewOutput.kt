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
import io.confluent.intellijplugin.core.ui.ExpansionPanel
import io.confluent.intellijplugin.core.util.invokeLater
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.*
import javax.swing.JPanel
import kotlin.math.max

/**
 * JCEF WebView implementation of KafkaRecordsOutput.
 * Maintains the same public API as KafkaRecordsOutput but uses a browser-based table.
 */
class KafkaRecordsWebViewOutput(val project: Project, val isProducer: Boolean) : IKafkaRecordsOutput {

    // Keep existing model for state management and fallback
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

    // JCEF Browser components
    private val browser = JBCefBrowser()
    private var isHtmlLoaded = false

    // JS Query for bidirectional communication
    private val jsQuery = JBCefJSQuery.create(browser as com.intellij.ui.jcef.JBCefBrowserBase).also { query ->
        query.addHandler { request ->
            handleBrowserMessage(request)
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
                executeJavaScript("window.clearRows()")
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
    }

    override fun dispose() {
        jsQuery.dispose()
        Disposer.dispose(browser)
    }

    override fun replace(output: List<KafkaRecord>) {
        outputModel.clear()
        output.forEach {
            outputModel.addElement(it)
        }
        val json = serializeRecords(output)
        executeJavaScript("window.replaceRows($json)")
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

        // Send to browser
        val json = serializeRecords(elements)
        executeJavaScript("window.receiveRows($json)")
    }

    override fun addError(element: KafkaRecord) {
        outputModel.addElement(element)
        val json = serializeRecords(listOf(element))
        executeJavaScript("window.receiveRows($json)")
    }

    override fun getElements(): List<KafkaRecord> {
        return outputModel.elements().toList()
    }

    private fun handleBrowserMessage(message: String) {
        // Parse message from JavaScript
        // Expected format: {"type":"ROW_SELECT","index":42}
        try {
            val parts = message.split(":")
            if (parts.size >= 2 && parts[0].contains("ROW_SELECT")) {
                val index = parts[1].trim().removeSuffix("}").toIntOrNull()
                if (index != null && index >= 0 && index < outputModel.rowCount) {
                    invokeLater {
                        updateDetails(index)
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore malformed messages
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

    private fun serializeRecords(records: List<KafkaRecord>): String {
        return records.joinToString(",", "[", "]") { record ->
            """
            {
              "topic": "${escapeJson(record.topic)}",
              "timestamp": ${record.timestamp},
              "key": "${escapeJson(record.keyText ?: "")}",
              "value": "${escapeJson(record.valueText ?: record.errorText)}",
              "partition": ${record.partition},
              "offset": ${record.offset}
            }
            """.trimIndent()
        }
    }

    private fun escapeJson(str: String): String {
        return str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun loadHtmlTemplate(): String {
        // Try to load from resources, fallback to embedded HTML
        return try {
            javaClass.getResourceAsStream("/html/kafka-consumer-table.html")?.bufferedReader()?.readText()
                ?: getEmbeddedHtml()
        } catch (_: Exception) {
            getEmbeddedHtml()
        }
    }

    private fun getEmbeddedHtml(): String {
        val queryInjection = jsQuery.inject("request")
        return """
<!DOCTYPE html>
<html>
<head>
    <style>
        body {
            margin: 0;
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            font-size: 13px;
            background: #ffffff;
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
            border-bottom: 1px solid #e0e0e0;
        }
        td {
            padding: 6px 8px;
            border-bottom: 1px solid #f0f0f0;
            max-width: 400px;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }
        tr:hover {
            background: #f7f7f7;
        }
        tr.selected {
            background: #cce5ff !important;
        }
        .loading {
            padding: 20px;
            text-align: center;
            color: #666;
        }
    </style>
</head>
<body>
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
    <div id="loading" class="loading" style="display: none;">Loading...</div>

    <script>
        let records = [];
        let selectedIndex = -1;
        let maxRows = 0;

        // Called from Kotlin
        window.receiveRows = function(newRows) {
            records.push(...newRows);

            // Enforce max rows
            if (maxRows > 0 && records.length > maxRows) {
                records = records.slice(-maxRows);
            }

            renderTable();
        };

        window.replaceRows = function(newRows) {
            records = newRows;
            selectedIndex = -1;
            renderTable();
        };

        window.setMaxRows = function(limit) {
            maxRows = limit;
        };

        window.clearRows = function() {
            records = [];
            selectedIndex = -1;
            renderTable();
        };

        window.setLoading = function(isLoading) {
            document.getElementById('loading').style.display = isLoading ? 'block' : 'none';
        };

        function renderTable() {
            const tbody = document.getElementById('tableBody');
            tbody.innerHTML = '';

            if (records.length === 0) {
                return;
            }

            records.forEach((record, index) => {
                const row = tbody.insertRow();
                row.className = index === selectedIndex ? 'selected' : '';
                row.onclick = () => selectRow(index);

                row.insertCell().textContent = record.topic;
                row.insertCell().textContent = formatTimestamp(record.timestamp);
                row.insertCell().textContent = record.key || '(null)';
                row.insertCell().textContent = record.value || '(null)';
                row.insertCell().textContent = record.partition;
                row.insertCell().textContent = record.offset;
            });
        }

        function formatTimestamp(timestamp) {
            const date = new Date(timestamp);
            return date.toLocaleString();
        }

        function selectRow(index) {
            selectedIndex = index;
            renderTable();

            // Send to Kotlin
            const message = 'ROW_SELECT:' + index;
            $queryInjection
        }
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
