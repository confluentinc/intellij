package io.confluent.kafka.core.ui

import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.impl.AnchoredButton
import com.intellij.toolWindow.StripeButtonUi
import com.intellij.ui.RelativeFont
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import javax.swing.Icon

/** Simple vertical button. Rework of private StripeButton for wider use. */
class VerticalButton(@NlsContexts.Button text: String, icon: Icon? = null, selected: Boolean = false)
  : AnchoredButton(text, icon, selected) {

  init {
    isFocusable = false
    border = JBUI.Borders.empty(5, 5, 0, 5)
    isRolloverEnabled = true
    isOpaque = false
  }

  override fun updateUI() {
    setUI(StripeButtonUi())
    val font = StartupUiUtil.labelFont
    val relativeFont = RelativeFont.NORMAL.fromResource("StripeButton.fontSizeOffset", 0, JBUIScale.scale(11f))
    setFont(relativeFont.derive(font))
  }

  override fun getMnemonic2(): Int = 0

  override fun getAnchor() = ToolWindowAnchor.LEFT

  companion object {
    fun createToolbar(@NlsContexts.Button text: String, icon: Icon? = null): VerticalButton {
      return VerticalButton(text, icon).apply {

        val i = JBUI.CurrentTheme.Toolbar.verticalToolbarInsets()
        border = if (i != null) {
          JBUI.Borders.empty(i.top, i.left, i.bottom, i.right)
        }
        else {
          JBUI.Borders.empty(7, 4)
        }

        horizontalAlignment = RIGHT
        verticalAlignment = TOP
        horizontalTextPosition = LEFT
      }
    }
  }
}