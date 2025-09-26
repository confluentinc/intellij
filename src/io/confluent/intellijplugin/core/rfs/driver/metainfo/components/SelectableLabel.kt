package io.confluent.intellijplugin.core.rfs.driver.metainfo.components

import com.intellij.ui.components.fields.ExpandableTextField
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.BorderFactory
import kotlin.math.max

/**
 * Special label to show in FileInfo panels and dialogs.
 * This label allows to select content and if the label cannot show whole content it will show an expand button.
 */
class SelectableLabel(text: String) : ExpandableTextField() {
  init {
    this.text = text
    isOpaque = false
    border = BorderFactory.createEmptyBorder()
    isEditable = false
    columns = 3

    setMonospaced(false)
    setExtensions(emptyList())

    addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) = updateExpandAction()
    })
  }

  override fun getPreferredSize(): Dimension {
    if (isPreferredSizeSet) {
      return super.getPreferredSize()
    }
    val parentPreferredSized = super.getPreferredSize()
    val metrics = getFontMetrics(font)
    return Dimension(max(parentPreferredSized.width, metrics.stringWidth(text) + margin.left + margin.right), super.getPreferredSize().height)
  }

  override fun setText(t: String?) {
    super.setText(t)
    updateExpandAction()
    // Without this fix, when the text is larger than visible area, it will be centered, not left aligned.
    caretPosition = 0
  }

  private fun updateExpandAction() {
    val metrics = getFontMetrics(font)
    if (metrics.stringWidth(text) + margin.left + margin.right > width) {
      if (extensions.isEmpty()) {
        setExtensions(createExtensions())
        repaint()
      }
    }
    else {
      if (extensions.isNotEmpty()) {
        setExtensions(emptyList())
        repaint()
      }
    }
  }
}