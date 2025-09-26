package io.confluent.intellijplugin.core.ui

import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.geom.Area
import java.awt.geom.Rectangle2D
import javax.swing.border.Border

/**
 * Special border used for JBTextArea and EditorTextField to make a JTextField-like border which also supports validation.
 * <pre>
 * {@code
 * JBTextArea().apply {
 *     border = BorderFactory.createCompoundBorder(DarculaTextAreaBorder(), ComponentColoredBorder(3, 5, 3, 5))
 * }
 * }
 * </pre>
 */
class ComponentColoredBorder(top: Int, left: Int, bottom: Int, right: Int) : Border {

  private val insets = Insets(top, left, bottom, right)

  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    val g2 = g.create() as Graphics2D
    try {
      val area = Area(Rectangle2D.Float(x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat()))
      area.subtract(Area(Rectangle2D.Float((x + insets.left).toFloat(),
                                           (y + insets.top).toFloat(),
                                           (width - (insets.left + insets.right)).toFloat(),
                                           (height - (insets.top + insets.bottom)).toFloat())))
      area.intersect(Area(g2.clip))
      g2.clip(area)

      g2.color = c.background
      g2.fillRect(x + 1, y + 1, width - 2, height - 2)
    }
    catch (e: Exception) {
      //
    }
    finally {
      g2.dispose()
    }
  }

  override fun getBorderInsets(c: Component?) = insets

  override fun isBorderOpaque() = false
}