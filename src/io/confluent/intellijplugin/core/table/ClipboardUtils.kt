package io.confluent.intellijplugin.core.table

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.ide.CopyPasteManager
import io.confluent.intellijplugin.common.editor.ListTableModel
import io.confluent.intellijplugin.core.table.model.DataFrameTableModel
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import java.awt.datatransfer.StringSelection
import javax.swing.JTable

/** Clipboard utils to realize Ctrl+C functionality in Table. */
object ClipboardUtils {

    const val LINE_SEPARATOR: String = "\r"

    private const val CELL_BREAK = "\t"

    private fun getNotificationGroup() = NotificationGroupManager.getInstance().getNotificationGroup("BDT Table")

    fun setStringContent(content: String): Unit = CopyPasteManager.getInstance().setContents(StringSelection(content))

    fun getAllAsString(table: JTable): String = getSelectedAsString(
        table, (0 until table.rowCount).toIntArray(),
        (0 until table.columnCount).toIntArray()
    )

    fun getSelectedAsString(table: JTable): String =
        getSelectedAsString(table, table.selectedRows, table.selectedColumns)

    fun IntRange.toIntArray(): IntArray {
        if (last < first)
            return IntArray(0)

        val result = IntArray(last - first + 1)
        var index = 0
        for (element in this) {
            result[index++] = element
        }

        return result
    }

    fun appendData(builder: StringBuilder, table: JTable, selectedRows: IntArray, selectedColumns: IntArray) {
        for (row in selectedRows.withIndex()) {
            for (column in selectedColumns.withIndex()) {
                resolveCellValue(table, row.value, column.value)?.let { builder.append(escape(it)) }

                if (column.index < selectedColumns.size - 1) {
                    builder.append(CELL_BREAK)
                }
            }

            if (row.index < selectedRows.size - 1) {
                builder.append(LINE_SEPARATOR)
            }
        }
    }

    private fun resolveCellValue(table: JTable, row: Int, column: Int): Any? {
        val modelRow = table.convertRowIndexToModel(row)
        val modelCol = table.convertColumnIndexToModel(column)

        val dataframe = (table.model as? DataFrameTableModel)?.dataFrame
        if (dataframe != null) {
            return if (!dataframe[modelCol].isNull(modelRow)) dataframe[modelCol][modelRow] else null
        }

        val listModel = table.model as? ListTableModel<*>
        if (listModel != null) {
            return listModel.getFullValueAt(modelRow, modelCol)
        }

        return table.getValueAt(row, column)
    }

    private fun getSelectedAsString(table: JTable, selectedRows: IntArray, selectedColumns: IntArray): String {

        if (selectedRows.isEmpty() || selectedColumns.isEmpty()) {
            val notification = getNotificationGroup().createNotification(
                KafkaMessagesBundle.message("copy.error.title"),
                KafkaMessagesBundle.message("copy.error.emptySelection"),
                NotificationType.INFORMATION
            )
            notification.notify(null)
            return ""
        }

        val builder = StringBuilder()
        appendData(builder, table, selectedRows, selectedColumns)

        return builder.toString()
    }

    fun escape(cell: Any): String {
        return cell.toString().replace(LINE_SEPARATOR, " ").replace(CELL_BREAK, " ")
    }
}