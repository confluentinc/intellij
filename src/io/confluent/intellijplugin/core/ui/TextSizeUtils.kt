package io.confluent.intellijplugin.core.ui

import java.awt.Font
import javax.swing.JLabel
import javax.swing.plaf.basic.BasicHTML
import javax.swing.text.View

/** If we want to get real height of text component with line wrapping, while width is fixed. */
object TextSizeUtils {
    private val fakeLabel = JLabel()

    fun getPreferredHeight(text: String, font: Font, prefWidth: Int): Int {
        fakeLabel.font = font
        @Suppress("HardCodedStringLiteral")
        fakeLabel.text = "<html>${text.replace("\n", "<br>")}</html>"

        val view = fakeLabel.getClientProperty(BasicHTML.propertyKey) as? View ?: return fakeLabel.preferredSize.height
        view.setSize(prefWidth.toFloat(), 0f)
        return view.getPreferredSpan(View.Y_AXIS).toInt()
    }
}