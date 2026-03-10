package io.confluent.intellijplugin.core.table.extension

import io.confluent.intellijplugin.core.table.ColumnWidthFittingStrategy
import io.confluent.intellijplugin.core.table.MaterialTableUtils
import io.confluent.intellijplugin.core.table.renderers.MaterialTableCellRenderer
import java.awt.event.HierarchyBoundsAdapter
import java.awt.event.HierarchyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JTable
import javax.swing.SwingUtilities
import javax.swing.event.*
import javax.swing.table.AbstractTableModel
import kotlin.math.min

/**
 * Realization of smart table resize.
 * Sometimes we want to stretch only one or group of columns and leave the rest column width untouched.
 */
class TableResizeController private constructor(private val table: JTable) : TableModelListener {

    companion object {
        fun getFor(table: JTable): TableResizeController? = (table.model as? AbstractTableModel)?.getListeners(
            TableResizeController::class.java
        )?.firstOrNull()

        fun installOn(table: JTable): TableResizeController {
            return getFor(table) ?: TableResizeController(table)
        }
    }

    enum class Mode {
        STRETCH_LAST_COLUMN,
        PRIOR_COLUMNS_LIST
    }

    var mode = Mode.STRETCH_LAST_COLUMN

    var onlyExpand = false
    private var adjustWidth = true

    private var priorColumns: List<String> = emptyList()

    private var columResizingInProgress = false
        private set(value) {
            if (field != value) {
                field = value
                if (!field) {
                    componentResized()
                }
            }
        }

    private val headerMouseListener = object : MouseAdapter() {
        override fun mouseReleased(e: MouseEvent?) {
            columResizingInProgress = false
        }
    }

    private val columnListener = object : TableColumnModelListener {
        override fun columnMarginChanged(e: ChangeEvent?) {
            if (table.tableHeader.resizingColumn != null) {
                columResizingInProgress = true
            }
        }

        override fun columnMoved(e: TableColumnModelEvent) {
            val lastColumn = table.columnCount - 1

            if (e.fromIndex != lastColumn && e.toIndex != lastColumn) return

            (table.columnModel.getColumn(e.fromIndex).cellRenderer as? MaterialTableCellRenderer)?.lastColumn =
                e.fromIndex == lastColumn
            (table.columnModel.getColumn(e.toIndex).cellRenderer as? MaterialTableCellRenderer)?.lastColumn =
                e.toIndex == lastColumn
        }

        override fun columnAdded(e: TableColumnModelEvent?) = tableChanged(null)
        override fun columnRemoved(e: TableColumnModelEvent?) = componentResized()
        override fun columnSelectionChanged(e: ListSelectionEvent?) {}
    }

    init {
        table.autoResizeMode = JTable.AUTO_RESIZE_OFF

        table.addHierarchyBoundsListener(object : HierarchyBoundsAdapter() {
            override fun ancestorResized(e: HierarchyEvent) {
                componentResized()
            }
        })

        table.tableHeader.addMouseListener(headerMouseListener)
        table.columnModel.addColumnModelListener(columnListener)

        if (adjustWidth) {
            if (table.rowCount > 0 && table.columnCount > 0) {
                MaterialTableUtils.fitColumnsWidth(table)
            } else {
                table.model.addTableModelListener(this)
            }
        }
    }

    override fun tableChanged(e: TableModelEvent?) {
        if (table.rowCount > 0 && table.columnCount > 0) {
            MaterialTableUtils.fitColumnsWidth(table)
            SwingUtilities.invokeLater {
                componentResized()
            }
            table.model.removeTableModelListener(this)
        }
    }

    fun setResizePriorityList(vararg columns: String): TableResizeController {
        priorColumns = columns.toList()
        return this
    }

    fun componentResized() {
        if (table.columnCount == 0 || columResizingInProgress || table.parent == null || table.parent.width == 0) {
            return
        }

        if (mode == Mode.STRETCH_LAST_COLUMN) {

            val lastColumn = table.columnModel.getColumn(table.columnCount - 1)

            val columnsWidth = table.columnModel.columns.toList().sumOf { it.width } - lastColumn.width
            val tableWidth = table.parent.width
            if (tableWidth < columnsWidth) {
                return
            }

            lastColumn.preferredWidth = tableWidth - columnsWidth
        } else if (mode == Mode.PRIOR_COLUMNS_LIST) {

            if (priorColumns.isEmpty()) {
                return
            }

            val tableColumnsList = table.columnModel.columns.toList()

            val nonPriorPreferredWidth =
                tableColumnsList.filter { !priorColumns.contains(it.headerValue) }.sumOf { it.preferredWidth }
            val totalWidth = table.parent.width

            if (onlyExpand) {
                val preferredColumns = tableColumnsList.filter { priorColumns.contains(it.headerValue) }
                val priorPreferredWidth = preferredColumns.sumOf { it.preferredWidth }
                val priorIdealWidth = preferredColumns.sumOf {
                    MaterialTableUtils.getColumnWidth(table, it.modelIndex, ColumnWidthFittingStrategy.PERCENTILE_BASED)
                }

                if (totalWidth < nonPriorPreferredWidth + min(priorPreferredWidth, priorIdealWidth)) {
                    return
                }
            } else if (totalWidth < nonPriorPreferredWidth) {
                return
            }

            val priorWidth = (totalWidth - nonPriorPreferredWidth) / priorColumns.size
            tableColumnsList.forEach {
                if (priorColumns.contains(it.headerValue)) it.preferredWidth = priorWidth
            }
        }

        table.revalidate()
        table.repaint()
    }
}
