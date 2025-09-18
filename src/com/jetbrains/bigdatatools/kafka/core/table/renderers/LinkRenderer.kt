package com.jetbrains.bigdatatools.kafka.core.table.renderers

import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.event.ChangeEvent
import javax.swing.event.ListSelectionEvent
import javax.swing.event.TableColumnModelEvent
import javax.swing.event.TableColumnModelListener
import javax.swing.table.TableColumn

/** Cell renderer renders cell content as link. */
class LinkRenderer : MaterialTableCellRenderer(), MouseListeningRenderer {

  companion object {
    fun installOnColumn(table: JTable, column: TableColumn): LinkRenderer {
      val renderer = LinkRenderer()
      column.cellRenderer = renderer
      renderer.column = column

      table.apply {
        removeMouseListener(renderer)
        removeMouseMotionListener(renderer)
        addMouseListener(renderer)
        addMouseMotionListener(renderer)
      }

      // For the case when column was hidden and than shown again.
      table.columnModel.addColumnModelListener(object : TableColumnModelListener {
        override fun columnAdded(e: TableColumnModelEvent) {
          for (i in e.fromIndex..e.toIndex) {
            val tableColumn = table.columnModel.getColumn(i)
            if (tableColumn.identifier == column.identifier) {
              tableColumn.cellRenderer = renderer
              renderer.column = tableColumn
            }
          }
        }

        override fun columnRemoved(e: TableColumnModelEvent?) = Unit
        override fun columnMoved(e: TableColumnModelEvent?) = Unit
        override fun columnMarginChanged(e: ChangeEvent?) = Unit
        override fun columnSelectionChanged(e: ListSelectionEvent?) = Unit
      })

      return renderer
    }
  }

  class Link : JLabel() {

    var hover = false

    var linkColor: Color? = null

    init {
      border = BorderFactory.createEmptyBorder(0, textLeftOffset, 0, 0)
      isOpaque = true
    }

    override fun paintComponent(g: Graphics) {

      foreground = linkColor

      super.paintComponent(g)

      if (hover) {
        g.color = foreground
        val boundsWidth = g.fontMetrics.stringWidth(text)
        val lineY = getUI().getBaseline(this, width, height) + 1
        val leftInset = border?.getBorderInsets(this)?.left ?: 0
        g.drawLine(leftInset, lineY, boundsWidth + leftInset, lineY)
      }
    }
  }

  /** This text will be rendered in cell instead of actual data. This text could be "Open logs" or "Sho in browser for example"*/
  var replacementText: String? = null

  /** Parameters - table row and column indexes under the pointer. */
  var onClick: ((Int, Int) -> Unit)? = null

  /** If specified, only clicks within this column will cause onClick invocation.*/
  var column: TableColumn? = null

  private val linkLabel = Link()

  private var hoverRow: Int = -1
  private var hoverColumn: Int = -1

  /**
   * If specified, linkLabel will be shown only when condition is true and default renderer will be shown otherwise.
   *
   * <pre>
   *     LinkRenderer.installOnColumn(table, columnModel.getColumn(0)).apply {
   *       condition = { table, _, row, _ ->
   *          @Suppress("UNCHECKED_CAST")
   *         (table.model as? DataTableModel<EmrClusterAppInfo>)?.getInfoAt(row)?.url?.isNotBlank() == true
   *       }
   *
   *       onClick = { row, _ ->
   *         executeOnPooledThread {
   *         showConnection(row)
   *       }
   *     }
   *   }
   * </pre>
   */
  var condition: ((table: JTable, value: Any?, row: Int, column: Int) -> Boolean)? = null

  override fun getTableCellRendererComponent(table: JTable, value: Any?, isSelected: Boolean,
                                             hasFocus: Boolean, row: Int, column: Int): Component {
    val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
    condition?.let { condition ->
      if (!condition.invoke(table, value, row, column)) {
        return component
      }
    }

    linkLabel.background = component.background
    linkLabel.linkColor = if (isSelected && table.isFocusOwner) {
      table.selectionForeground ?: UIUtil.getTableSelectionForeground(true)
    }
    else {
      JBUI.CurrentTheme.Link.Foreground.ENABLED
    }

    linkLabel.font = component.font
    linkLabel.text = replacementText ?: value?.toString() ?: ""
    linkLabel.hover = hoverRow == row && hoverColumn == column
    return linkLabel
  }

  override fun mouseEntered(e: MouseEvent?) {
    update(e)
  }

  override fun mouseClicked(e: MouseEvent?) {
    if (e?.button != MouseEvent.BUTTON1) {
      return
    }

    onClick?.let outer@{
      val table = e.source as? JTable ?: return
      val row = table.rowAtPoint(e.point)
      val column = table.columnAtPoint(e.point)

      if (row == -1 || column == -1) return

      condition?.let { condition ->
        if (!condition.invoke(table, table.getValueAt(row, column), row, column)) {
          return@outer
        }
      }

      if (this.column == null || this.column == table.columnModel.getColumn(column)) {
        it.invoke(row, column)
      }
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