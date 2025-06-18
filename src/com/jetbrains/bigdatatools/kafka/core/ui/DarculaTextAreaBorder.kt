package com.jetbrains.bigdatatools.kafka.core.ui

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ui.EditorTextField
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.MacUIUtil
import java.awt.*
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.border.Border
import javax.swing.text.JTextComponent

/**
 * Same border as for TextFields but for TextArea and even EditorTextField.
 * [com.intellij.ide.ui.laf.darcula.ui.DarculaTextBorder]
 */
class DarculaTextAreaBorder : Border {

  override fun getBorderInsets(c: Component?): Insets {
    val topBottom = if (DarculaUIUtil.isTableCellEditor(c) || DarculaUIUtil.isCompact(c)) 2 else 3
    return JBInsets.create(topBottom, 3).asUIResource()
  }

  override fun isBorderOpaque() = false

  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {

    val insets = getBorderInsets(c)

    val editable = c !is JTextComponent || c.isEditable
    val g2 = g.create() as Graphics2D
    try {
      val focused = isFocused(c)
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                          if (MacUIUtil.USE_QUARTZ) RenderingHints.VALUE_STROKE_PURE else RenderingHints.VALUE_STROKE_NORMALIZE)

      if (c.parent != null && c.parent.background != null) {
        // JTextArea hack! JTextArea is not transparent as EditorTextField, and we need to fill the area outside border.
        // And also we cannot make JTextArea.isOpaque=false, because we are loosing text background in this case.
        g2.color = c.parent.background
        g2.fillRect(x, y, insets.left, height)
        g2.fillRect(width - insets.right, y, insets.right, height)

        g2.fillRect(x, y, width, insets.top)
        g2.fillRect(x, height - insets.bottom, width, insets.bottom)
      }

      val op = DarculaUIUtil.getOutline(c as JComponent)
      if (c.isEnabled() && op != null) {
        g2.translate(1, 1)
        DarculaUIUtil.paintOutlineBorder(g2, width - 2, height - 2, 0f, true, focused, op)
        g2.translate(-1, -1)
      }
      else if (focused) {
        g2.translate(1, 1)
        DarculaUIUtil.paintOutlineBorder(g2, width - 2, height - 2, 0f, true, true, DarculaUIUtil.Outline.focus)
        g2.translate(-1, -1)
      }

      g2.color = DarculaUIUtil.getOutlineColor(c.isEnabled && editable, focused)
      g2.drawRect(x + insets.left, y + insets.top, width - insets.left - insets.right - 1, height - insets.top - insets.bottom - 1)
    }
    finally {
      g2.dispose()
    }
  }

  private fun isFocused(c: Component): Boolean {
    return when (c) {
      is JScrollPane -> c.viewport.view.hasFocus()
      is EditorTextField -> c.editor?.contentComponent?.hasFocus() == true
      else -> c.hasFocus()
    }
  }
}