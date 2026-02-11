package io.confluent.intellijplugin.consumer.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.text.DateFormat
import java.util.Base64
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JComponent

class JcefRecordsTable(
    private val columnNames: List<String>,
    private val isProducer: Boolean,
    parentDisposable: Disposable
) : Disposable {

    private val browser: JBCefBrowser
    private val jsQueryRowSelected: JBCefJSQuery

    @Volatile
    private var browserReady = false
    private val pendingBatches = CopyOnWriteArrayList<String>()

    var onRowSelected: ((Int) -> Unit)? = null

    val component: JComponent
        get() = browser.component

    init {
        val htmlContent = this::class.java.classLoader.getResourceAsStream("jcef/consumer-table.html")
            ?.bufferedReader()?.readText()
            ?: error("consumer-table.html not found in resources")

        browser = JBCefBrowser()
        jsQueryRowSelected = JBCefJSQuery.create(browser as JBCefBrowserBase)

        jsQueryRowSelected.addHandler { indexStr ->
            val index = indexStr.toIntOrNull() ?: return@addHandler null
            onRowSelected?.invoke(index)
            null
        }

        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(cefBrowser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                if (frame.isMain) {
                    injectRowSelectedCallback(cefBrowser)
                    initColumns(cefBrowser)
                    browserReady = true
                    flushPendingBatches(cefBrowser)
                }
            }
        }, browser.cefBrowser)

        browser.loadHTML(htmlContent)

        Disposer.register(parentDisposable, this)
    }

    fun addRows(records: List<KafkaRecord>) {
        val base64 = serializeRecords(records)
        if (browserReady) {
            executeJs("window.addRows('$base64')")
        } else {
            pendingBatches.add(base64)
        }
    }

    fun replaceAllRows(records: List<KafkaRecord>) {
        val base64 = serializeRecords(records)
        if (browserReady) {
            executeJs("window.replaceAllRows('$base64')")
        } else {
            pendingBatches.clear()
            pendingBatches.add(base64)
        }
    }

    fun clear() {
        pendingBatches.clear()
        if (browserReady) {
            executeJs("window.clearTable()")
        }
    }

    fun showLoading(message: String) {
        if (browserReady) {
            val escaped = message.replace("\\", "\\\\").replace("'", "\\'")
            executeJs("window.showLoading('$escaped')")
        }
    }

    fun hideLoading() {
        if (browserReady) {
            executeJs("window.hideLoading()")
        }
    }

    override fun dispose() {}

    private fun injectRowSelectedCallback(cefBrowser: CefBrowser) {
        val jsCallback = jsQueryRowSelected.inject("index")
        cefBrowser.executeJavaScript(
            "window.__onRowSelected = function(index) { $jsCallback };",
            cefBrowser.url, 0
        )
    }

    private fun initColumns(cefBrowser: CefBrowser) {
        // Use internal column keys that JS uses for field lookup
        val colKeys = buildJsonArray {
            for (name in columnNames) {
                add(JsonPrimitive(name))
            }
        }
        val base64 = Base64.getEncoder().encodeToString(colKeys.toString().toByteArray())
        cefBrowser.executeJavaScript(
            "window.initColumns(JSON.parse(atob('$base64')))",
            cefBrowser.url, 0
        )
    }

    private fun flushPendingBatches(cefBrowser: CefBrowser) {
        val batches = ArrayList(pendingBatches)
        pendingBatches.clear()
        for (batch in batches) {
            cefBrowser.executeJavaScript("window.addRows('$batch')", cefBrowser.url, 0)
        }
    }

    private fun serializeRecords(records: List<KafkaRecord>): String {
        val jsonArray = buildJsonArray {
            for (record in records) {
                add(buildJsonObject {
                    for (i in columnNames.indices) {
                        val colName = columnNames[i]
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
        return Base64.getEncoder().encodeToString(jsonArray.toString().toByteArray())
    }

    private fun getColumnValue(record: KafkaRecord, columnIndex: Int): Any? {
        return when (columnIndex) {
            0 -> record.topic
            1 -> if (record.timestamp >= 0) DATE_FORMAT.format(Date(record.timestamp)) else ""
            2 -> record.keyText ?: ""
            3 -> record.valueText ?: record.errorText
            4 -> record.partition
            5 -> if (isProducer) record.duration else record.offset
            else -> ""
        }
    }

    private fun executeJs(code: String) {
        try {
            browser.cefBrowser.executeJavaScript(code, browser.cefBrowser.url, 0)
        } catch (e: Exception) {
            thisLogger().warn("Failed to execute JS in JCEF consumer table", e)
        }
    }

    companion object {
        private val DATE_FORMAT: DateFormat = DateFormat.getDateTimeInstance()
    }
}
