package io.confluent.intellijplugin.core.table.renderers

import com.intellij.openapi.editor.impl.FontInfo
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JComponent

class CellProgressBar : JComponent() {

  companion object {
    val progressColor = JBColor(Color(165, 205, 255), Color(85, 108, 138))
    val unfinishedProgressColor = JBColor(Color(223, 236, 252), Color(68, 78, 92))
    val offset = JBUIScale.scale(4)
  }

  var maximumValue = 0
  var value = 0

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)

    g.color = background
    g.fillRect(0, 0, width, height)

    val g2d = g as Graphics2D
    g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
    g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

    g.color = if (value == maximumValue) progressColor else unfinishedProgressColor
    val diameter = (height - offset * 2)
    g.fillRoundRect(offset, offset, (width * value.toDouble() / maximumValue).toInt() - offset * 2, diameter, diameter, diameter)

    g.color = UIUtil.getLabelForeground()
    g.font = UIUtil.getLabelFont().deriveFont(UIUtil.getFontSize(UIUtil.FontSize.SMALL))

    val text = "$value/$maximumValue"
    val fontMetrics = FontInfo.getFontMetrics(g2d.font, g2d.fontRenderContext)
    val bounds = g2d.font.createGlyphVector(g2d.fontRenderContext, text).visualBounds
    g.drawString(text, ((width - bounds.width) / 2).toInt(), (height + fontMetrics.ascent - fontMetrics.descent) / 2)
  }
}