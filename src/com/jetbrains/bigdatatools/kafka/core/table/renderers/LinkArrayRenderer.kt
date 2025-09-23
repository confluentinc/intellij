package io.confluent.kafka.core.table.renderers

import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.MouseInfo
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.SwingUtilities

@Suppress("DuplicatedCode")
class LinkArrayRenderer : MaterialTableCellRenderer(), MouseListeningRenderer {

  class LinkArray : JLabel() {

    var arr: List<String> = emptyList()
      set(value) {
        field = value
        text = value.joinToString(", ")
      }

    var hover = false

    var linkColor: Color? = null

    // X coordinate of start and end of
    var start = -1
    var end = -1

    init {
      border = BorderFactory.createEmptyBorder(0, textLeftOffset, 0, 0)
      isOpaque = true
    }

    override fun paintComponent(g: Graphics) {

      foreground = linkColor

      super.paintComponent(g)

      if (hover && start != -1) {
        g.color = foreground

        val lineY = getUI().getBaseline(this, width, height) + 1
        g.drawLine(start, lineY, end, lineY)
      }
    }
  }

  var onClick: ((element: String) -> Unit)? = null

  private val linkLabel = LinkArray()

  private var hoverRow: Int = -1
  private var hoverColumn: Int = -1

  private fun getClickedIndexAndRange(table: JTable, row: Int, column: Int, data: List<String>): Pair<Int, IntRange>? {
    // Finding range where the mouse cursor is
    val mousePosition = MouseInfo.getPointerInfo().location
    SwingUtilities.convertPointFromScreen(mousePosition, table)

    val rect = table.getCellRect(row, column, false)
    mousePosition.x -= rect.x
    mousePosition.y -= rect.y

    val fontMetrics = linkLabel.getFontMetrics(linkLabel.font)

    val spacingWidth = fontMetrics.stringWidth(", ")

    var x = linkLabel.border.getBorderInsets(linkLabel).left
    for (ar in data.withIndex()) {
      val boundsWidth = fontMetrics.stringWidth(ar.value)
      if (mousePosition.x >= x && mousePosition.x <= x + boundsWidth) {
        return Pair(ar.index, x..x + boundsWidth)
      }
      x += (boundsWidth + spacingWidth)
    }

    return null
  }

  override fun getTableCellRendererComponent(table: JTable, value: Any?, isSelected: Boolean,
                                             hasFocus: Boolean, row: Int, column: Int): Component {
    val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

    @Suppress("UNCHECKED_CAST")
    val arr = value as? List<String> ?: return component

    linkLabel.background = component.background
    linkLabel.linkColor = if (isSelected && table.isFocusOwner) {
      component.foreground ?: table.selectionForeground
    }
    else {
      JBUI.CurrentTheme.Link.Foreground.ENABLED
    }

    linkLabel.font = component.font
    linkLabel.arr = arr
    linkLabel.hover = hoverRow == row && hoverColumn == column

    if (linkLabel.hover) {

      linkLabel.start = -1
      linkLabel.end = -1

      if (arr.isNotEmpty()) {
        getClickedIndexAndRange(table, row, column, arr)?.let {
          linkLabel.start = it.second.first
          linkLabel.end = it.second.last
        }
      }
    }

    return linkLabel
  }

  override fun mouseEntered(e: MouseEvent?) {
    update(e)
  }

  override fun mouseClicked(e: MouseEvent?) {

    onClick ?: return

    e ?: return
    val table = e.component as? JTable ?: return
    val row: Int = table.rowAtPoint(e.point)
    val column: Int = table.columnAtPoint(e.point)
    if (row == -1 || column == -1) return
    @Suppress("UNCHECKED_CAST")
    val arr = table.getValueAt(row, column) as? List<String> ?: return

    getClickedIndexAndRange(table, row, column, arr)?.let {
      onClick?.invoke(arr[it.first])
    }
  }

  override fun mouseExited(e: MouseEvent?) {
    if (hoverColumn == -1 || hoverRow == -1) {
      return
    }

    val table = e?.source as? JTable ?: return
    table.repaint(table.getCellRect(hoverRow, hoverColumn, false))

    hoverColumn = -1
    hoverRow = -1
  }

  override fun mouseMoved(e: MouseEvent?) {
    update(e)
  }

  private fun update(e: MouseEvent?) {
    val table = e?.source as? JTable ?: return
    val row = table.rowAtPoint(e.point)
    val col = table.columnAtPoint(e.point)

    if (hoverColumn == col && hoverRow == row) {
      table.repaint(table.getCellRect(hoverRow, hoverColumn, false))
      return
    }

    if (hoverRow != -1 && hoverColumn != -1) {
      table.repaint(table.getCellRect(hoverRow, hoverColumn, false))
    }

    hoverRow = row
    hoverColumn = col

    if (hoverRow != -1 && hoverColumn != -1) {
      table.repaint(table.getCellRect(hoverRow, hoverColumn, false))
    }
  }
}