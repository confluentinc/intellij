package io.confluent.intellijplugin.actions.dev.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.ui.table.JBTable
import io.confluent.intellijplugin.consumer.editor.KafkaRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.text.DateFormat
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.table.AbstractTableModel

data class BenchmarkResult(
    val durationMs: Double,
    val rowCount: Int
)

interface TableBenchmark : Disposable {
    val name: String
    val component: JComponent
    suspend fun waitUntilReady()
    suspend fun benchmarkAddRows(records: List<KafkaRecord>): BenchmarkResult
    suspend fun benchmarkReplaceAll(records: List<KafkaRecord>): BenchmarkResult
    suspend fun cleanup()
}

private val COLUMNS = listOf("Topic", "Timestamp", "Key", "Value", "Partition", "Offset")
private val DATE_FORMAT: DateFormat = DateFormat.getDateTimeInstance()

private fun getColumnValue(record: KafkaRecord, columnIndex: Int): Any? {
    return when (columnIndex) {
        0 -> record.topic
        1 -> if (record.timestamp >= 0) DATE_FORMAT.format(Date(record.timestamp)) else ""
        2 -> record.keyText ?: ""
        3 -> record.valueText ?: record.errorText
        4 -> record.partition
        5 -> record.offset
        else -> ""
    }
}

// --- JTable implementation ---

class JTableBenchmark(parentDisposable: Disposable) : TableBenchmark {
    override val name = "JTable"

    private val data = mutableListOf<KafkaRecord>()
    private val model = object : AbstractTableModel() {
        override fun getRowCount() = data.size
        override fun getColumnCount() = COLUMNS.size
        override fun getColumnName(column: Int) = COLUMNS[column]
        override fun getValueAt(rowIndex: Int, columnIndex: Int) = getColumnValue(data[rowIndex], columnIndex)
    }
    private val table = JBTable(model).apply {
        setShowGrid(true)
        autoResizeMode = JBTable.AUTO_RESIZE_ALL_COLUMNS
    }
    private val scrollPane = JBScrollPane(table)

    override val component: JComponent get() = scrollPane

    init {
        Disposer.register(parentDisposable, this)
    }

    override suspend fun waitUntilReady() {
        // JTable is ready immediately
    }

    override suspend fun benchmarkAddRows(records: List<KafkaRecord>): BenchmarkResult {
        var result: BenchmarkResult? = null
        SwingUtilities.invokeAndWait {
            val startIndex = data.size
            val t0 = System.nanoTime()

            data.addAll(records)
            model.fireTableRowsInserted(startIndex, data.size - 1)
            table.doLayout()
            if (table.isShowing) {
                table.paintImmediately(table.visibleRect)
            }

            val durationMs = (System.nanoTime() - t0) / 1_000_000.0
            result = BenchmarkResult(durationMs, records.size)
        }
        return result!!
    }

    override suspend fun benchmarkReplaceAll(records: List<KafkaRecord>): BenchmarkResult {
        var result: BenchmarkResult? = null
        SwingUtilities.invokeAndWait {
            val t0 = System.nanoTime()

            data.clear()
            data.addAll(records)
            model.fireTableDataChanged()
            table.doLayout()
            if (table.isShowing) {
                table.paintImmediately(table.visibleRect)
            }

            val durationMs = (System.nanoTime() - t0) / 1_000_000.0
            result = BenchmarkResult(durationMs, records.size)
        }
        return result!!
    }

    override suspend fun cleanup() {
        SwingUtilities.invokeAndWait {
            data.clear()
            model.fireTableDataChanged()
        }
    }

    override fun dispose() {
        data.clear()
    }
}

// --- JCEF implementation ---

class JcefTableBenchmark(parentDisposable: Disposable) : TableBenchmark {
    override val name = "JCEF"

    private val browser: JBCefBrowser
    private val perfQuery: JBCefJSQuery

    @Volatile private var browserReady = false
    @Volatile private var pendingResult: CompletableFuture<BenchmarkResult>? = null
    private val readyFuture = CompletableFuture<Unit>()

    override val component: JComponent get() = browser.component

    init {
        val html = this::class.java.classLoader
            .getResourceAsStream("jcef/benchmark-table.html")
            ?.bufferedReader()?.readText()
            ?: error("benchmark-table.html not found in resources")

        browser = JBCefBrowser()
        perfQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)

        perfQuery.addHandler { json ->
            try {
                val obj = Json.parseToJsonElement(json).jsonObject
                val durationMs = obj["durationMs"]?.jsonPrimitive?.double ?: 0.0
                val rowCount = obj["rowCount"]?.jsonPrimitive?.int ?: 0
                pendingResult?.complete(BenchmarkResult(durationMs, rowCount))
            } catch (e: Exception) {
                thisLogger().error("Failed to parse perf callback", e)
                pendingResult?.completeExceptionally(e)
            }
            null
        }

        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(cefBrowser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                if (frame.isMain) {
                    // Wire up __perfCallback
                    val jsCallback = perfQuery.inject("data")
                    cefBrowser.executeJavaScript(
                        "window.__perfCallback = function(data) { $jsCallback };",
                        cefBrowser.url, 0
                    )
                    // Init columns
                    val colJson = buildJsonArray {
                        for (col in COLUMNS) add(JsonPrimitive(col))
                    }.toString()
                    val base64 = Base64.getEncoder().encodeToString(colJson.toByteArray(Charsets.UTF_8))
                    cefBrowser.executeJavaScript(
                        "window.initColumns(JSON.parse(atob('$base64')))",
                        cefBrowser.url, 0
                    )
                    browserReady = true
                    readyFuture.complete(Unit)
                }
            }
        }, browser.cefBrowser)

        browser.loadHTML(html)

        Disposer.register(parentDisposable, this)
        Disposer.register(this, browser)
    }

    override suspend fun waitUntilReady() {
        if (browserReady) return
        withContext(Dispatchers.IO) {
            readyFuture.get(10, TimeUnit.SECONDS)
        }
    }

    override suspend fun benchmarkAddRows(records: List<KafkaRecord>): BenchmarkResult {
        val base64 = serializeRecords(records)
        val future = CompletableFuture<BenchmarkResult>()
        pendingResult = future
        SwingUtilities.invokeLater {
            browser.cefBrowser.executeJavaScript(
                "window.addRows('$base64')", browser.cefBrowser.url, 0
            )
        }
        return withContext(Dispatchers.IO) {
            future.get(60, TimeUnit.SECONDS)
        }
    }

    override suspend fun benchmarkReplaceAll(records: List<KafkaRecord>): BenchmarkResult {
        val base64 = serializeRecords(records)
        val future = CompletableFuture<BenchmarkResult>()
        pendingResult = future
        SwingUtilities.invokeLater {
            browser.cefBrowser.executeJavaScript(
                "window.replaceAllRows('$base64')", browser.cefBrowser.url, 0
            )
        }
        return withContext(Dispatchers.IO) {
            future.get(60, TimeUnit.SECONDS)
        }
    }

    override suspend fun cleanup() {
        SwingUtilities.invokeLater {
            browser.cefBrowser.executeJavaScript(
                "window.clearTableSilent()", browser.cefBrowser.url, 0
            )
        }
        delay(100)
    }

    override fun dispose() {
        browserReady = false
        pendingResult?.cancel(true)
    }

    private fun serializeRecords(records: List<KafkaRecord>): String {
        val jsonArray = buildJsonArray {
            for (record in records) {
                add(buildJsonObject {
                    for (i in COLUMNS.indices) {
                        val colName = COLUMNS[i]
                        val value = getColumnValue(record, i)
                        when (value) {
                            null -> put(colName, JsonNull)
                            is Number -> put(colName, JsonPrimitive(value))
                            else -> put(colName, JsonPrimitive(value.toString()))
                        }
                    }
                    put("isError", JsonPrimitive(record.error != null))
                })
            }
        }
        return Base64.getEncoder().encodeToString(jsonArray.toString().toByteArray(Charsets.UTF_8))
    }
}
