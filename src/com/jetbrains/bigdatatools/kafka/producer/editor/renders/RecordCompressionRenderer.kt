package com.jetbrains.bigdatatools.kafka.producer.editor.renders

import com.jetbrains.bigdatatools.kafka.producer.models.RecordCompression
import javax.swing.DefaultListCellRenderer
import javax.swing.JList

class RecordCompressionRenderer : DefaultListCellRenderer() {
  override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int,
                                            isSelected: Boolean, cellHasFocus: Boolean) =
    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus).apply {
      val recordCompression = value as? RecordCompression ?: return@apply
      @Suppress("HardCodedStringLiteral")
      text = recordCompression.name.toLowerCase()
    }
}