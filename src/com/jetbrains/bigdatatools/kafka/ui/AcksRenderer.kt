package com.jetbrains.bigdatatools.kafka.ui

import javax.swing.DefaultListCellRenderer
import javax.swing.JList

class AcksRenderer : DefaultListCellRenderer() {
  override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int,
                                            isSelected: Boolean, cellHasFocus: Boolean) =
    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus).apply {
      val topicPresentable = value as? AcksType ?: return@apply
      @Suppress("HardCodedStringLiteral")
      text = topicPresentable.name.toLowerCase()
    }
}