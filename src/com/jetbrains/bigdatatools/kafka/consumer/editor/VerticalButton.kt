package com.jetbrains.bigdatatools.kafka.consumer.editor

import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.impl.AnchoredButton
import com.intellij.openapi.wm.impl.StripeButtonUI
import com.intellij.ui.RelativeFont
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import javax.swing.Icon

class VerticalButton(@NlsContexts.Button text: String, icon: Icon, selected: Boolean) : AnchoredButton(text, icon, selected) {

  init {
    isFocusable = false
    border = JBUI.Borders.empty(5, 5, 0, 5)
    isRolloverEnabled = true
    isOpaque = false
  }

  override fun updateUI() {
    setUI(StripeButtonUI.createUI(this))
    val font = StartupUiUtil.getLabelFont()
    val relativeFont = RelativeFont.NORMAL.fromResource("StripeButton.fontSizeOffset", -2, JBUIScale.scale(11f))
    setFont(relativeFont.derive(font))
  }

  override fun getMnemonic2(): Int = 0

  override fun getAnchor() = ToolWindowAnchor.LEFT
}