package io.confluent.intellijplugin.core.ui

import com.intellij.ide.HelpTooltip
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.Container
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.JComponent

fun Component.onFirstSizeChange(runnable: (e: ComponentEvent) -> Unit) {
    addComponentListener(object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent) {
            runnable(e)
            removeComponentListener(this)
        }
    })
}

fun JComponent.onDoubleClick(runnable: (e: MouseEvent) -> Unit) {
    addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
            if (e.button == MouseEvent.BUTTON1 && e.clickCount == 2) {
                runnable(e)
            }
        }
    })
}

fun Component.applyRecursively(action: (JComponent) -> Unit) {
    val front = LinkedList<Component>().apply { add(this@applyRecursively) }

    while (!front.isEmpty()) {
        val comp = front.remove()
        if (comp is JComponent) {
            action.invoke(comp)
        }
        if (comp is Container) {
            front.addAll(comp.components)
        }
    }
}

fun JComponent.withTooltip(@Nls text: String): JComponent {
    HelpTooltip().setDescription(text).installOn(this)
    return this
}