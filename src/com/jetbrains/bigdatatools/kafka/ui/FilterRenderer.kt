package com.jetbrains.bigdatatools.kafka.ui

import com.jetbrains.bigdatatools.kafka.consumer.ConsumerFilterType
import javax.swing.DefaultListCellRenderer
import javax.swing.JList

class FilterRenderer : DefaultListCellRenderer() {
  override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int,
                                            isSelected: Boolean, cellHasFocus: Boolean) =
    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus).apply {
      @Suppress("HardCodedStringLiteral")
      text = (value as? ConsumerFilterType)?.name
    }
}