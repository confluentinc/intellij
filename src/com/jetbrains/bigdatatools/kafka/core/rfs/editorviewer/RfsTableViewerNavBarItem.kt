package com.jetbrains.bigdatatools.kafka.core.rfs.editorviewer

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.RelativeFont
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Insets
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.plaf.basic.BasicGraphicsUtils

/** NavBarItem displayed in path navigation bar in RfsTableViewerEditor. For the first item, the arrow will not be displayed. */
class RfsTableViewerNavBarItem(@NlsContexts.Button text: String, first: Boolean) : JButton(text, null) {
  init {
    isFocusPainted = false
    isBorderPainted = false
    isRolloverEnabled = true
    horizontalAlignment = LEFT
    isContentAreaFilled = false
    background = null
    font = RelativeFont.NORMAL.fromResource("NavBar.fontSizeOffset", 0).derive(font)
    foreground = JBUI.CurrentTheme.StatusBar.Breadcrumbs.FOREGROUND
    border = BorderFactory.createEmptyBorder(2, 2, 2, 5)

    if (!first) {
      icon = AllIcons.General.ChevronRight
    }

    addMouseListener(object : MouseAdapter() {
      override fun mouseEntered(evt: MouseEvent) {
        background = JBUI.CurrentTheme.StatusBar.Breadcrumbs.HOVER_BACKGROUND
        foreground = JBUI.CurrentTheme.StatusBar.Breadcrumbs.SELECTION_FOREGROUND
      }

      override fun mouseExited(evt: MouseEvent) {
        background = null
        foreground = JBUI.CurrentTheme.StatusBar.Breadcrumbs.FOREGROUND
      }
    })
  }

  override fun getPreferredSize(): Dimension {
    return BasicGraphicsUtils.getPreferredButtonSize(this, 4)
  }

  override fun getMargin() = JBInsets(0, 0, 0, 0)

  override fun getInsets(insets: Insets?) = JBInsets(0, 0, 0, 0)

  override fun paintComponent(g: Graphics) {
    if (background != null) {
      g.color = background
      g.fillRoundRect(0, 0, width, height, 3, 3)
    }
    super.paintComponent(g)
  }
}