package com.jetbrains.bigdatatools.kafka.core.ui

import com.intellij.icons.AllIcons
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.scale.JBUIScale

/** Does nothing, only adds Search icon at the beginning of component. */
class SearchExtension : ExtendableTextComponent.Extension {
  override fun getIcon(hovered: Boolean) = AllIcons.Actions.Search
  override fun isIconBeforeText() = true
  override fun getIconGap() = JBUIScale.scale(10)
}