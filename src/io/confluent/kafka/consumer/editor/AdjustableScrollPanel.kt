package io.confluent.kafka.consumer.editor

import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Dimension
import javax.swing.BorderFactory
import kotlin.math.min

/* Special scroll pane used for text presentation of keys and values. */
class AdjustableScrollPanel(view: Component) : JBScrollPane(view) {
  init {
    // In the other case the borders will be removed when the component placed in the Editor.
    putClientProperty(UIUtil.KEEP_BORDER_SIDES, SideBorder.ALL)
    border = BorderFactory.createLineBorder(JBColor.border())
  }

  override fun getPreferredSize(): Dimension {
    val superSize = super.getPreferredSize()
    return Dimension(superSize.width,
                     min(JBUIScale.scale(500),
                         superSize.height + (if (horizontalScrollBar?.isVisible == true) horizontalScrollBar.height * 2 else 0)))
  }
}