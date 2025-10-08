package io.confluent.intellijplugin.core.table.renderers

import com.intellij.icons.AllIcons
import com.intellij.util.ui.EmptyIcon
import java.awt.Component
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableColumn

@Suppress("DuplicatedCode")
class FavoriteRenderer private constructor() : JLabel(), TableCellRenderer, MouseListeningRenderer {
    var onClick: ((Int, Int) -> Unit)? = null
    var column: TableColumn? = null

    private var isHover = false
    private var hoverRow: Int = -1
    private var hoverColumn: Int = -1

    init {
        isOpaque = true
        horizontalAlignment = SwingConstants.CENTER
        verticalAlignment = SwingConstants.CENTER
        border = BorderFactory.createEmptyBorder()
    }

    override fun getTableCellRendererComponent(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        isHover = hoverRow == row && hoverColumn == column
        val isFavourite = value as? Boolean
        return this.apply {
            icon = if (isFavourite == true)
                AllIcons.Nodes.Favorite
            else if (isHover)
                AllIcons.Nodes.NotFavoriteOnHover
            else EmptyIcon.ICON_16
        }
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

            if (this.column == null || this.column == table.columnModel.getColumn(column)) {
                it.invoke(row, column)
            }
        }
    }

    override fun mouseEntered(e: MouseEvent?) {
        update(e)
    }

    override fun mouseMoved(e: MouseEvent?) {
        update(e)
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

    companion object {
        fun installOnColumn(table: JTable, column: TableColumn): FavoriteRenderer {
            val renderer = FavoriteRenderer()
            column.cellRenderer = renderer
            column.headerValue = ""
            column.preferredWidth = 36
            renderer.column = column

            table.apply {
                removeMouseListener(renderer)
                removeMouseMotionListener(renderer)
                addMouseListener(renderer)
                addMouseMotionListener(renderer)
            }

            return renderer
        }
    }
}