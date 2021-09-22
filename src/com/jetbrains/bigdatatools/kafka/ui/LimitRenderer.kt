package com.jetbrains.bigdatatools.kafka.ui

import javax.swing.DefaultListCellRenderer
import javax.swing.JList

class LimitRenderer : DefaultListCellRenderer() {
  override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int,
                                            isSelected: Boolean, cellHasFocus: Boolean) =
    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus).apply {
      @Suppress("HardCodedStringLiteral")
      text = (value as? ConsumerLimit)?.name
    }
}