package io.confluent.intellijplugin.core.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ToolbarLabelAction
import com.intellij.openapi.util.NlsActions
import javax.swing.JComponent

class ToolbarGreyLabelActionImpl(@NlsActions.ActionText text: String) : ToolbarLabelAction() {
  init {
    templatePresentation.text = text
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return super.createCustomComponent(presentation, place).apply {
      isEnabled = false
    }
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}