package com.jetbrains.bigdatatools.kafka.core.table.editor

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.DumbAware
import com.jetbrains.bigdatatools.kafka.core.table.ClipboardUtils
import com.jetbrains.bigdatatools.kafka.core.table.ClipboardUtils.toIntArray
import com.jetbrains.bigdatatools.kafka.core.table.MaterialTable

abstract class BdiTableEditorActionBase : AnAction(), DumbAware {

  companion object {
    const val BDI_TABLE_NOTIFICATION_GROUP: String = "BDT Settings"

    const val COPY_TABLE_HEADER_KEY: String = "bdt.table.copy.header"
  }

  // can be null in dumb mode
  fun getTable(e: AnActionEvent): MaterialTable? = e.getData(MaterialTable.BDI_TABLE)
}

class BdiTableCopyRowAction : BdiTableEditorActionBase() {
  override fun actionPerformed(e: AnActionEvent) {
    val table = getTable(e) ?: return
    val buffer = StringBuilder()
    ClipboardUtils.appendData(buffer, table, table.selectedRows, (0 until table.columnCount).toIntArray())
    ClipboardUtils.setStringContent(buffer.toString())
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = getTable(e)?.isCopyEnabled(DataContext.EMPTY_CONTEXT) == true
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

class BdiTableCopyColumnAction : BdiTableEditorActionBase() {
  override fun actionPerformed(e: AnActionEvent) {
    val table = getTable(e) ?: return
    val buffer = StringBuilder()
    ClipboardUtils.appendData(buffer, table, (0 until table.rowCount).toIntArray(), table.selectedColumns)
    ClipboardUtils.setStringContent(buffer.toString())
    ClipboardUtils.setStringContent(buffer.toString())
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = getTable(e)?.isCopyEnabled(DataContext.EMPTY_CONTEXT) == true
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

abstract class BdiTableDumpActionBase : BdiTableEditorActionBase() {

  override fun update(e: AnActionEvent) {
    val table = getTable(e)
    val valid = table != null && table.rowCount > 0
    e.presentation.isEnabledAndVisible = valid
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  fun getTableFullText(e: AnActionEvent): String {
    val table = getTable(e) ?: return ""
    return ClipboardUtils.getAllAsString(table)
  }
}

class BdiTableDumpToClipboardAction : BdiTableDumpActionBase() {
  override fun actionPerformed(e: AnActionEvent) {
    ClipboardUtils.setStringContent(getTableFullText(e))
  }
}
