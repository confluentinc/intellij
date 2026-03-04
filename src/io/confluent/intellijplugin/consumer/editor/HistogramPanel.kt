package io.confluent.intellijplugin.consumer.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.MouseInfo
import java.awt.Point
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.*

class HistogramPanel(parentDisposable: Disposable) : Disposable {

    private val browser: JBCefBrowser = JBCefBrowser().also {
        Disposer.register(this, it)
    }

    @Volatile
    private var browserReady = false

    private val pendingCalls = CopyOnWriteArrayList<String>()

    private var tooltipPopup: Popup? = null

    private val tooltipQuery = JBCefJSQuery.create(browser).also {
        Disposer.register(this, it)
    }

    val component: JComponent = JPanel(BorderLayout()).apply {
        preferredSize = Dimension(0, HISTOGRAM_HEIGHT)
        maximumSize = Dimension(Int.MAX_VALUE, HISTOGRAM_HEIGHT)
        minimumSize = Dimension(0, HISTOGRAM_HEIGHT)
        add(browser.component, BorderLayout.CENTER)
    }

    init {
        Disposer.register(parentDisposable, this)

        tooltipQuery.addHandler { msg ->
            SwingUtilities.invokeLater {
                if (msg.isBlank()) {
                    hideTooltip()
                } else {
                    showTooltip(msg)
                }
            }
            JBCefJSQuery.Response(null)
        }

        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(cefBrowser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                browserReady = true
                // Inject the JS query callback function
                val injection = "window.__tooltipCallback = function(msg) { ${tooltipQuery.inject("msg")} };"
                cefBrowser?.executeJavaScript(injection, "", 0)
                flushPending()
            }
        }, browser.cefBrowser)

        val html = javaClass.getResourceAsStream("/jcef/histogram.html")?.bufferedReader()?.readText()
        if (html != null) {
            browser.loadHTML(html)
        } else {
            thisLogger().error("histogram.html not found in resources")
        }
    }

    private fun showTooltip(htmlContent: String) {
        hideTooltip()
        val mouseLocation = MouseInfo.getPointerInfo()?.location ?: return

        val label = JLabel(htmlContent).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(java.awt.Color.GRAY),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
            )
            isOpaque = true
            background = java.awt.Color(40, 40, 40)
            foreground = java.awt.Color.WHITE
        }

        val tipPoint = Point(mouseLocation.x + 12, mouseLocation.y + 16)
        tooltipPopup = PopupFactory.getSharedInstance().getPopup(component, label, tipPoint.x, tipPoint.y)
        tooltipPopup?.show()
    }

    private fun hideTooltip() {
        tooltipPopup?.hide()
        tooltipPopup = null
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

    override fun dispose() {
        hideTooltip()
    }

    companion object {
        private const val HISTOGRAM_HEIGHT = 80
    }
}
