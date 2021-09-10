package com.jetbrains.bigdatatools.kafka.ui

import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.JList

class FieldTypeRenderer : DefaultListCellRenderer() {
  override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int,
                                            isSelected: Boolean, cellHasFocus: Boolean): Component =
    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus).apply {
      val topicPresentable = value as? FieldType ?: return@apply
      @Suppress("HardCodedStringLiteral")
      text = topicPresentable.value
    }
}