package com.jetbrains.bigdatatools.kafka.ui

import com.jetbrains.bigdatatools.kafka.model.TopicPresentable
import javax.swing.DefaultListCellRenderer
import javax.swing.JList

class TopicRenderer : DefaultListCellRenderer() {
  override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int,
                                            isSelected: Boolean, cellHasFocus: Boolean) =
    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus).apply {
      val topicPresentable = value as? TopicPresentable ?: return@apply
      @Suppress("HardCodedStringLiteral")
      text = topicPresentable.name
    }
}