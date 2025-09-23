package io.confluent.kafka.core.table.renderers

import com.intellij.util.ui.JBUI
import java.awt.Component
import javax.swing.JCheckBox
import javax.swing.JTable
import javax.swing.UIManager
import javax.swing.border.Border
import javax.swing.plaf.UIResource
import javax.swing.table.TableCellRenderer

class BooleanRenderer : JCheckBox(), TableCellRenderer, UIResource {
  override fun getTableCellRendererComponent(table: JTable,
                                             value: Any?,
                                             isSelected: Boolean,
                                             hasFocus: Boolean,
                                             row: Int,
                                             column: Int): Component {
    if (isSelected) {
      foreground = table.selectionForeground
      super.setBackground(table.selectionBackground)
    }
    else {
      foreground = table.foreground
      background = table.background
    }
    setSelected(value != null && (value as Boolean))
    border = if (hasFocus) UIManager.getBorder("Table.focusCellHighlightBorder") else noFocusBorder
    return this
  }

  companion object {
    private val noFocusBorder: Border = JBUI.Borders.empty(1)
  }

  init {
    horizontalAlignment = CENTER
    isBorderPainted = true
  }
}