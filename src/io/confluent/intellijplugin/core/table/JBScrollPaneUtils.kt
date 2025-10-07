package io.confluent.intellijplugin.core.table

import com.intellij.ui.components.JBScrollBar
import com.intellij.ui.components.JBScrollPane
import java.awt.event.MouseWheelEvent
import java.lang.reflect.Method

object JBScrollPaneUtils {

    private val getDeltaAdjusted: Method? = try {
        JBScrollBar::class.java.getDeclaredMethod("getDeltaAdjusted", MouseWheelEvent::class.java)
            .apply { isAccessible = true }
    } catch (_: Exception) {
        null
    }

    fun disableHorizontalWheelRedispatch(scrollPane: JBScrollPane?) {

        if (getDeltaAdjusted == null || scrollPane == null) {
            return
        }

        val oldListeners = scrollPane.mouseWheelListeners.copyOf()
        oldListeners.forEach { scrollPane.removeMouseWheelListener(it) }

        scrollPane.addMouseWheelListener { event ->
            if (event.isShiftDown && event.wheelRotation > 0) {
                val bar = scrollPane.horizontalScrollBar
                val isAdjustedDeltaZero = bar is JBScrollBar && getDeltaAdjusted!!(bar, event) == 0.0
                if (isAdjustedDeltaZero) {
                    event.consume()
                }
            }
        }
        oldListeners.forEach { scrollPane.addMouseWheelListener(it) }
    }
}