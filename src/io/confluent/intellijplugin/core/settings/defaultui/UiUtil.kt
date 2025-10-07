package io.confluent.intellijplugin.core.settings.defaultui

import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.DocumentAdapter
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import java.awt.Component
import java.awt.Container
import java.util.*
import javax.swing.JComponent
import javax.swing.event.DocumentEvent
import javax.swing.text.JTextComponent

object UiUtil {

    private const val defaultLeftGap = "12"
    private const val macOsComboGap = "3"

    // MigLayout presets (Component constraints)
    val wrap: CC = CC().wrap()
    val pushXGrowXWrap: CC = CC().pushX().growX().wrap()
    val pushXGrowXSpanXWrap: CC = CC().pushX().growX().spanX().wrap()
    val macOsPushXGrowXSpanXWrap: CC = CC().pushX().growX().spanX().wrap().gapRight(macOsComboGap)

    val growXSpanXWrap: CC = CC().growX().spanX().wrap()
    val spanXWrap: CC = CC().spanX().wrap()

    val gapLeft: CC = CC().gapLeft(defaultLeftGap)
    val gapLeftWrap: CC = CC().gapLeft(defaultLeftGap).wrap()
    val gapLeftSpanXWrap: CC = CC().gapLeft(defaultLeftGap).spanX().wrap()
    val macOsGapLeftSpanXWrap: CC = CC().gapLeft(defaultLeftGap).spanX().wrap().gapRight(macOsComboGap)
    val gapLeftGrowXSpanXWrap: CC = CC().gapLeft(defaultLeftGap).growX().spanX().wrap()
    val separator: CC = CC().gapTop("10").growX().spanX().wrap()

    val insets0FillXHidemode3: LC = LC().insets("0").fillX().hideMode(3)
    val insets10FillXHidemode3: LC = LC().insets("10").fillX().hideMode(3)

    inline fun <reified T : JComponent> getFirstChildComponent(parent: Container): T? {
        val front = LinkedList<Component>().apply {
            addAll(parent.components)
        }

        while (!front.isEmpty()) {
            val comp = front.remove()
            if (comp is T) {
                return comp
            }
            if (comp is Container) {
                front.addAll(comp.components)
            }
        }

        return null
    }
}

fun registerOnTextComponent(textComponent: JTextComponent, listener: HostAndPortChangeListener) {
    textComponent.document.addDocumentListener(
        object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                listener.onChange()
            }
        }
    )
}

fun connectionError(infos: List<ValidationInfo>): ConnectionError = ConnectionError(
    error = null,
    additionalErrorDescription = infos.joinToString("<br>") { it.message },
    shortDescription = KafkaMessagesBundle.message("validator.settings.contain.errors")
)