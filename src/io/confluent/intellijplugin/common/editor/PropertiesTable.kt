package io.confluent.intellijplugin.common.editor

import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.ui.JBColor
import com.intellij.ui.TableUtil
import com.intellij.ui.ToolbarDecorator
import io.confluent.intellijplugin.core.settings.components.BdtPropertyComponent
import io.confluent.intellijplugin.core.settings.connections.Property
import io.confluent.intellijplugin.core.table.MaterialTable
import io.confluent.intellijplugin.producer.editor.HeadersTablePasteProvider
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.JTable

class PropertiesTable(data: List<Property>, val isEditable: Boolean = true) {
  constructor(data: String) : this(BdtPropertyComponent.parseProperties(data))

  private val tableModel = PropertiesTableModel(data.toMutableList(), isEditable)
  val table = object : MaterialTable(tableModel, tableModel.columnModel) {
    init {
      autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
      tableHeader.border = BorderFactory.createEmptyBorder()
      background = JBColor.WHITE
      tableHeader.background = JBColor.WHITE
    }

    override fun uiDataSnapshot(sink: DataSink) {
      super.uiDataSnapshot(sink)
      sink[PlatformDataKeys.PASTE_PROVIDER] = pasteProvider
    }
  }
  private val component = createDecoratedTable()

  private val pasteProvider = if (isEditable) HeadersTablePasteProvider(this) else null

  fun addEntries(rows: List<Pair<String, String>>) {
    rows.forEach {
      tableModel.addRow(Property(it.first, it.second))
    }
  }

  var properties: MutableList<Property>
    get() = tableModel.properties
    set(value) {
      tableModel.properties = value
    }

  private fun createDecoratedTable(): JPanel {
    val createDecorator = ToolbarDecorator.createDecorator(table)
    if (isEditable) {
      createDecorator.setAddAction {
        tableModel.addRow(Property("", ""))
        val tableIndex = table.convertRowIndexToView(tableModel.rowCount - 1)
        table.setRowSelectionInterval(tableIndex, tableIndex)
        table.editCellAt(tableIndex, 0)
        TableUtil.scrollSelectionToVisible(table)
      }.setRemoveAction {
        table.selectedRows.sortedDescending().forEach {
          val modelIndex = table.convertRowIndexToModel(it)
          tableModel.removeRow(modelIndex)
        }
      }
    }

    return createDecorator.setScrollPaneBorder(BorderFactory.createEmptyBorder()).createPanel()
  }

  fun clear() {
    tableModel.clear()
  }

  fun getComponent() = component
}