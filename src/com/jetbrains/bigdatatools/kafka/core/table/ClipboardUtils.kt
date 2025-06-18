package com.jetbrains.bigdatatools.kafka.core.table

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.ide.CopyPasteManager
import com.jetbrains.bigdatatools.kafka.core.table.model.DataFrameTableModel
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import java.awt.datatransfer.StringSelection
import javax.swing.JTable

/** Clipboard utils to realize Ctrl+C functionality in Table. */
object ClipboardUtils {

  const val LINE_SEPARATOR = "\r"

  private const val CELL_BREAK = "\t"

  private fun getNotificationGroup() = NotificationGroupManager.getInstance().getNotificationGroup("BDT Table")

  fun setStringContent(content: String) = CopyPasteManager.getInstance().setContents(StringSelection(content))

  fun getSelectedAsString(table: JTable) = getSelectedAsString(table, table.selectedRows, table.selectedColumns)

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

  private fun appendHeader(builder: StringBuilder, table: JTable, selectedColumns: IntArray) {
    for (i in selectedColumns.indices) {
      val column = table.columnModel.getColumn(selectedColumns[i])
      column.headerValue?.let { builder.append(escape(it)) }
      if (i < selectedColumns.size - 1) {
        builder.append(CELL_BREAK)
      }
    }
    builder.append(LINE_SEPARATOR)
  }

  fun appendData(builder: StringBuilder, table: JTable, selectedRows: IntArray, selectedColumns: IntArray) {
    val dataframe = (table.model as? DataFrameTableModel)?.dataFrame

    for (row in selectedRows.withIndex()) {
      for (column in selectedColumns.withIndex()) {

        if (dataframe != null) {
          val modelRow = table.convertRowIndexToModel(row.value)
          val modelColumn = table.convertColumnIndexToModel(column.value)

          if (!dataframe[modelColumn].isNull(modelRow)) {
            dataframe[modelColumn][modelRow]?.let { builder.append(escape(it)) }
          }
        }
        else {
          table.getValueAt(row.value, column.value)?.let { builder.append(escape(it)) }
        }

        if (column.index < selectedColumns.size - 1) {
          builder.append(CELL_BREAK)
        }
      }

      if (row.index < selectedRows.size - 1) {
        builder.append(LINE_SEPARATOR)
      }
    }
  }

  private fun getSelectedAsString(table: JTable, selectedRows: IntArray, selectedColumns: IntArray): String {

    if (selectedRows.isEmpty() || selectedColumns.isEmpty()) {
      val notification = getNotificationGroup().createNotification(KafkaMessagesBundle.message("copy.error.title"),
                                                                   KafkaMessagesBundle.message("copy.error.emptySelection"),
                                                                   NotificationType.INFORMATION)
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