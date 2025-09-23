package io.confluent.kafka.core.monitoring.table.extension

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import io.confluent.kafka.core.monitoring.table.model.DataTableColumnModel
import io.confluent.kafka.core.settings.ColumnVisibilitySettings
import io.confluent.kafka.core.table.DecoratableDataTableModel
import io.confluent.kafka.core.table.MaterialTable
import io.confluent.kafka.core.table.renderers.DataRenderingUtil
import io.confluent.kafka.core.table.renderers.MouseListeningRenderer
import javax.swing.table.TableCellRenderer

class TableRenderersSetter private constructor(private val table: MaterialTable,
                                               private val columnSettings: ColumnVisibilitySettings?,
                                               private val customRendererSupplier: ((Array<out Annotation>, DecoratableDataTableModel?) -> TableCellRenderer?)?)
  : Disposable {

  companion object {
    fun installOn(table: MaterialTable,
                  columnSettings: ColumnVisibilitySettings? = null,
                  customRendererSupplier: ((Array<out Annotation>, DecoratableDataTableModel?) -> TableCellRenderer?)? = null) {
      val tableRenderersSetter = TableRenderersSetter(table, columnSettings, customRendererSupplier)
      Disposer.register(table, tableRenderersSetter)
    }
  }

  init {
    val columnModel = table.columnModel as? DataTableColumnModel<*>
    val tableModel = table.model as? DecoratableDataTableModel

    if (columnModel != null && tableModel != null) {
      columnModel.allColumns.withIndex().forEach { column ->
        val annotations = column.value.getAnnotations()
        val renderer = customRendererSupplier?.invoke(annotations, tableModel) ?: DataRenderingUtil.getRenderer(annotations, tableModel)

        renderer?.let {
          columnModel.getModelColumn(column.index)?.cellRenderer = it

          if (it is MouseListeningRenderer) {
            table.removeMouseListener(it)
            table.removeMouseMotionListener(it)

            table.addMouseListener(it)
            table.addMouseMotionListener(it)
          }
        }

      }
    }

    columnSettings?.let { it.onColumnVisibilityChanged += ::onColumnVisibilityChanged }
  }


  private fun onColumnVisibilityChanged(columnName: String, visible: Boolean) {

    if (!visible) return

    val columnModel = table.columnModel as? DataTableColumnModel<*> ?: return
    val tableModel = table.model as? DecoratableDataTableModel ?: return

    val modelIndex = columnModel.getModelIndex(columnName)

    val column = columnModel.getModelColumn(modelIndex) ?: return
    if (columnModel.allColumns.size <= modelIndex) return

    val field = columnModel.allColumns[modelIndex]
    val annotations = field.getAnnotations()
    val renderer = customRendererSupplier?.invoke(annotations, tableModel) ?: DataRenderingUtil.getRenderer(annotations, tableModel)
    renderer?.let {
      column.cellRenderer = it

      if (it is MouseListeningRenderer) {
        table.removeMouseListener(it)
        table.removeMouseMotionListener(it)

        table.addMouseListener(it)
        table.addMouseMotionListener(it)
      }
    }
  }

  override fun dispose() {
    columnSettings?.let { it.onColumnVisibilityChanged -= ::onColumnVisibilityChanged }
  }
}