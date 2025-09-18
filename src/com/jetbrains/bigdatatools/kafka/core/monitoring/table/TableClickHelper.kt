package com.jetbrains.bigdatatools.kafka.core.monitoring.table

import com.intellij.ide.BrowserUtil
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import com.jetbrains.bigdatatools.kafka.core.monitoring.data.model.RemoteInfo
import com.jetbrains.bigdatatools.kafka.core.monitoring.list.ListClickHelper
import com.jetbrains.bigdatatools.kafka.core.rfs.url.UrlDriverChooserAction
import com.jetbrains.bigdatatools.kafka.core.rfs.util.withSlash
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JTable
import kotlin.reflect.KProperty1

/** Assign a callback handler, called on click on specified column cell. Callback get whole Object representing table row. */
object TableClickHelper {

  fun <T : RemoteInfo> installOn(table: DataTable<T>, columns: List<String>, callback: (T, Any?) -> Unit) {

    table.addMouseListener(object : MouseAdapter() {

      val targetColumnIndexes = columns.map { table.tableModel.columnModel.getModelIndex(it) }.toHashSet()

      override fun mouseClicked(e: MouseEvent) {
        if (e.button != MouseEvent.BUTTON1) {
          return
        }
        val row = table.rowAtPoint(e.point)
        val col = table.columnAtPoint(e.point)
        val modelColumnIndex = table.convertColumnIndexToModel(col)
        if (row < 0 || col < 0 || !targetColumnIndexes.contains(modelColumnIndex)) {
          return
        }

        val obj = table.getDataAt(row) ?: return
        val modelRowIndex = table.convertRowIndexToModel(row)
        callback(obj, table.tableModel.getValueAt(modelRowIndex, modelColumnIndex))
      }
    })
  }

  fun <T : RemoteInfo> installBrowseOn(table: DataTable<T>, renderableColumns: List<KProperty1<T, *>>) {
    installOn(table, ListClickHelper.getBrowsableColumns(renderableColumns)) { _, url: Any? ->
      (url as? String)?.let {
        BrowserUtil.open(it)
      }
    }
  }

  fun showPopupUnderCell(project: Project, location: String?, dataTable: JTable, row: Int, column: Int) {
    location ?: return
    showPopupUnderCell(UrlDriverChooserAction(project, location.withSlash()), dataTable, row, column)
  }

  fun showPopupUnderCell(actionGroup: ActionGroup, dataTable: JTable, row: Int, column: Int) {
    val popupMenu = JBPopupFactory.getInstance().createActionGroupPopup(null, actionGroup,
                                                                        DataManager.getInstance().getDataContext(dataTable),
                                                                        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false)
    if (row == -1 || column == -1) {
      popupMenu.showUnderneathOf(dataTable)
    }
    else {
      val rect = dataTable.getCellRect(row, column, true)
      popupMenu.show(RelativePoint(dataTable, Point(rect.location.x, rect.location.y + rect.height)))
    }
  }
}