package io.confluent.kafka.core.ui

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import java.awt.Dimension

/** Helper method for creating JComponent button from AnAction. */
fun AnAction.createButton(dimension: Dimension = ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE): ActionButton {
  return ActionButton(this, null, "", dimension)
}