package io.confluent.kafka.core.rfs.ui

import com.intellij.openapi.util.NlsContexts
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import java.awt.Graphics
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JButton

class HoverButton(@NlsContexts.Button text: String, icon: Icon? = null) : JButton(text, icon) {
  init {
    isFocusPainted = false
    isBorderPainted = false
    isRolloverEnabled = true
    horizontalAlignment = LEFT
    isContentAreaFilled = false
    background = null

    addMouseListener(object : MouseAdapter() {
      override fun mouseEntered(evt: MouseEvent) {
        background = JBUI.CurrentTheme.Tree.Selection.background(true)
        foreground = NamedColorUtil.getListSelectionForeground(true)
      }

      override fun mouseExited(evt: MouseEvent) {
        background = null
        foreground = UIUtil.getLabelForeground()
      }
    })
  }

  override fun paintComponent(g: Graphics) {
    if (background != null) {
      g.color = background
      g.fillRect(0, 0, width, height)
    }
    super.paintComponent(g)
  }
}