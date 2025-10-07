package io.confluent.intellijplugin.core.ui

import com.intellij.openapi.Disposable
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.ui.StatusText
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class StatusTextAnimator(private val statusText: StatusText, private val texts: List<String>) : Disposable {

    private var currentIndex = 0

    private var ticker: ScheduledFuture<*>? = null

    fun start() {
        currentIndex = 0
        ticker = EdtExecutorService.getScheduledExecutorInstance()
            .scheduleWithFixedDelay({
                currentIndex++
                if (currentIndex >= texts.size) {
                    currentIndex = 0
                }
                statusText.text = texts[currentIndex]
                statusText.component.repaint()
            }, 0, 400, TimeUnit.MILLISECONDS)
    }

    fun stop() {
        ticker?.cancel(true)
        ticker = null
    }

    override fun dispose() {
        ticker?.cancel(true)
        ticker = null
    }
}