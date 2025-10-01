package io.confluent.intellijplugin.core.rfs.editorviewer

import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import io.confluent.intellijplugin.core.rfs.search.impl.ListElement
import java.awt.Component
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer

/** Special renderer for files table first column with file name and icon. Also a special rendering of Load more...*/
class RfsTableViewerCellRenderer : DefaultTableCellRenderer() {
  override fun getTableCellRendererComponent(table: JTable?,
                                             value: Any?,
                                             isSelected: Boolean,
                                             hasFocus: Boolean,
                                             row: Int,
                                             column: Int): Component {
    val entry = value as ListElement
    val component = super.getTableCellRendererComponent(table, entry.fileInfo.name, isSelected, hasFocus, row, column)
    icon = entry.icon
    // For rendering "Load more" element.
    if (value.icon == EmptyIcon.ICON_16) {
      text = "<html><u>${entry.fileInfo.name}</u></html>"
      foreground = JBUI.CurrentTheme.Link.Foreground.ENABLED
    }
    else {
      foreground = JBUI.CurrentTheme.Label.foreground()
    }
    return component
  }
}