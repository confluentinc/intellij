package io.confluent.intellijplugin.core.rfs.editorviewer

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.ui.OnePixelSplitter
import io.confluent.intellijplugin.core.rfs.driver.Driver
import io.confluent.intellijplugin.core.settings.ColumnVisibilitySettings
import io.confluent.intellijplugin.core.table.removeColumn
import javax.swing.JScrollBar
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.table.TableColumn

object RfsColumnVisibility {

  fun createAction(driver: Driver, table: JTable): AnAction {
    val allColumns = driver.getMetaInfoProvider().getAllTableColumns()

    val visibleColumnNames = allColumns.filter {
      RfsFileViewerSettings.getInstance().isColumnVisible(driver, it.id)
    }.map { it.name }.toMutableList()

    val columnVisibilitySettings = ColumnVisibilitySettings(visibleColumnNames)

    columnVisibilitySettings.onColumnVisibilityChanged += { columnName: String, visible: Boolean ->
      if (!visible) {
        table.removeColumn(columnName)
        val columnId = allColumns.firstOrNull { it.name == columnName }?.id
        columnId?.let { RfsFileViewerSettings.getInstance().hideColumn(driver, it) }
      }
      else {
        val foundColumn = allColumns.firstOrNull { it.name == columnName }
        foundColumn?.let {
          val index = driver.getMetaInfoProvider().getAllTableColumns().indexOf(it)
          table.addColumn(TableColumn(index).apply { headerValue = columnName })
          RfsFileViewerSettings.getInstance().showColumn(driver, it.id)
        }
      }
    }

    return ColumnVisibilitySettings.createAction(columnVisibilitySettings, allColumns.map { it.name })
  }

  fun createAction(driver: Driver, treeTable: RfsTreeTable): AnAction {
    val columns = driver.getMetaInfoProvider().getAllTableColumns().toMutableList()

    val visibleColumnNames = columns.filter {
      RfsFileViewerSettings.getInstance().isColumnVisible(driver, it.id)
    }.map { it.name }.toMutableList()

    val columnVisibilitySettings = ColumnVisibilitySettings(visibleColumnNames)

    columnVisibilitySettings.onColumnVisibilityChanged += { columnName: String, visible: Boolean ->
      val table = treeTable.table
      if (!visible) {
        val columnsBefore = table.columnModel.columnCount
        table.removeColumn(columnName)
        val columnId = columns.firstOrNull { it.name == columnName }?.id
        columnId?.let { RfsFileViewerSettings.getInstance().hideColumn(driver, it) }
        if (table.columnModel.columnCount == 0 && columnsBefore != 0) {
          onNoMoreTableColumns(treeTable)
        }
      }
      else {
        val foundColumn = columns.firstOrNull { it.name == columnName }
        val columnsBefore = table.columnModel.columnCount
        foundColumn?.let {
          val index = driver.getMetaInfoProvider().getAllTableColumns().indexOf(it) + 1
          table.addColumn(TableColumn(index).apply { headerValue = columnName })
          RfsFileViewerSettings.getInstance().showColumn(driver, it.id)
        }
        if (columnsBefore == 0 && table.columnModel.columnCount != 0) {
          onFirstTableColumn(treeTable)
        }
      }
    }

    return ColumnVisibilitySettings.createAction(columnVisibilitySettings, columns.map { it.name })
  }

  fun scrollBarSetup(driver: Driver, treeTable: RfsTreeTable) {
    if (!RfsFileViewerSettings.getInstance().hasVisibleColumns(driver)) {
      onNoMoreTableColumns(treeTable)
    }
  }

  private fun onNoMoreTableColumns(treeTable: RfsTreeTable) {
    val component = treeTable.components.firstOrNull() ?: return
    val split = component as? OnePixelSplitter ?: return
    val treePane = split.firstComponent as? JScrollPane ?: return
    val tablePane = split.secondComponent as? JScrollPane ?: return

    val verticalScrollBar = tablePane.verticalScrollBar
    tablePane.verticalScrollBar = JScrollBar()
    treePane.verticalScrollBar = verticalScrollBar

    split.secondComponent.isVisible = false
  }

  private fun onFirstTableColumn(treeTable: RfsTreeTable) {
    val component = treeTable.components.firstOrNull() ?: return
    val split = component as? OnePixelSplitter ?: return
    val treePane = split.firstComponent as? JScrollPane ?: return
    val tablePane = split.secondComponent as? JScrollPane ?: return

    split.secondComponent.isVisible = true
    tablePane.verticalScrollBar = treePane.verticalScrollBar
  }
}