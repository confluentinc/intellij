package com.jetbrains.bigdatatools.kafka.consumer.editor.renders

import com.jetbrains.bigdatatools.kafka.consumer.models.ConsumerLimitType
import javax.swing.DefaultListCellRenderer
import javax.swing.JList

class LimitRenderer : DefaultListCellRenderer() {
  override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int,
                                            isSelected: Boolean, cellHasFocus: Boolean) =
    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus).apply {
      @Suppress("HardCodedStringLiteral")
      text = (value as? ConsumerLimitType)?.name
    }
}