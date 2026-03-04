package io.confluent.intellijplugin.consumer.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowser
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.Dimension
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JComponent

class HistogramPanel(parentDisposable: Disposable) : Disposable {

    private val browser: JBCefBrowser = JBCefBrowser().also {
        Disposer.register(this, it)
    }

    @Volatile
    private var browserReady = false

    private val pendingCalls = CopyOnWriteArrayList<String>()

    val component: JComponent
        get() = browser.component.apply {
            preferredSize = Dimension(0, HISTOGRAM_HEIGHT)
            maximumSize = Dimension(Int.MAX_VALUE, HISTOGRAM_HEIGHT)
            minimumSize = Dimension(0, HISTOGRAM_HEIGHT)
        }

    init {
        Disposer.register(parentDisposable, this)

        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(cefBrowser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                browserReady = true
                flushPending()
            }
        }, browser.cefBrowser)

        val histogramUrl = javaClass.getResource("/jcef/histogram.html")
        if (histogramUrl != null) {
            browser.loadURL(histogramUrl.toExternalForm())
        } else {
            thisLogger().error("histogram.html not found in resources")
        }
    }

    fun addRecords(records: List<KafkaRecord>) {
        if (records.isEmpty()) return

        val jsonEntries = records.map { r ->
            """{"ts":${r.timestamp},"p":${r.partition}}"""
        }
        val json = "[${jsonEntries.joinToString(",")}]"
        val base64 = Base64.getEncoder().encodeToString(json.toByteArray())

        executeJs("window.addRecords('$base64')")
    }

    fun clear() {
        executeJs("window.clearHistogram()")
    }

    private fun executeJs(js: String) {
        if (browserReady) {
            browser.cefBrowser.executeJavaScript(js, "", 0)
        } else {
            pendingCalls.add(js)
        }
    }

    private fun flushPending() {
        val calls = ArrayList(pendingCalls)
        pendingCalls.clear()
        for (js in calls) {
            browser.cefBrowser.executeJavaScript(js, "", 0)
        }
    }

    override fun dispose() {}

    companion object {
        private const val HISTOGRAM_HEIGHT = 150
    }
}
