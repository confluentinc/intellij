package com.jetbrains.bigdatatools.kafka.ui

import javax.swing.DefaultListCellRenderer
import javax.swing.JList

class StartFromRenderer : DefaultListCellRenderer() {
  override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int,
                                            isSelected: Boolean, cellHasFocus: Boolean) =
    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus).apply {
      val topicPresentable = value as? ConsumerStartFrom ?: return@apply
      @Suppress("HardCodedStringLiteral")
      text = (value as? ConsumerStartFrom)?.name
    }
}