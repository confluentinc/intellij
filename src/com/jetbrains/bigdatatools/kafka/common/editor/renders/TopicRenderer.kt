package com.jetbrains.bigdatatools.kafka.common.editor.renders

import com.jetbrains.bigdatatools.kafka.common.models.TopicInEditor
import javax.swing.DefaultListCellRenderer
import javax.swing.JList

class TopicRenderer : DefaultListCellRenderer() {
  override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int,
                                            isSelected: Boolean, cellHasFocus: Boolean) =
    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus).apply {
      val topicPresentable = value as? TopicInEditor ?: return@apply
      @Suppress("HardCodedStringLiteral")
      text = topicPresentable.name
    }
}