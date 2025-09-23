package io.confluent.kafka.core.monitoring.table

import com.intellij.openapi.util.Disposer
import com.intellij.ui.TableSpeedSearch
import io.confluent.kafka.core.monitoring.data.model.RemoteInfo
import io.confluent.kafka.core.monitoring.table.extension.*
import io.confluent.kafka.core.monitoring.table.model.DataTableModel
import io.confluent.kafka.core.table.extension.TableResizeController
import java.util.*
import javax.swing.BorderFactory
import javax.swing.ListSelectionModel

object DataTableCreator {

  fun <T : RemoteInfo> create(tableModel: DataTableModel<T>,
                              extensions: EnumSet<TableExtensionType> = EnumSet.noneOf(TableExtensionType::class.java)): DataTable<T> {
    val columnModel = tableModel.columnModel
    val columnsSettings = columnModel.columnSettings
    val table = DataTable(tableModel, columnModel)

    Disposer.register(table, tableModel)
    Disposer.register(table, columnModel)

    extensions.forEach { extension: TableExtensionType ->
      when (extension) {
        TableExtensionType.SPEED_SEARCH -> TableSpeedSearch.installOn(table)
        TableExtensionType.RENDERERS_SETTER -> TableRenderersSetter.installOn(table, columnsSettings)
        TableExtensionType.COLUMNS_FITTER -> TableColumnsFitter.installOn(table, columnsSettings)
        TableExtensionType.ERROR_HANDLER -> TableErrorHandler.installOn(table)
        TableExtensionType.SELECTION_PRESERVER -> TableSelectionPreserver.installOn(table)
        TableExtensionType.LOADING_INDICATOR -> TableLoadingDecorator.installOn(table)
        TableExtensionType.FIRST_ROW_AUTOSELECTOR -> TableRowAutoselector.installOn(table)
        TableExtensionType.SMART_RESIZER -> TableResizeController.installOn(table)
        TableExtensionType.MULTI_SELECT -> table.selectionModel.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
      }
    }

    table.tableHeader.border = BorderFactory.createEmptyBorder()
    table.oneAndHalfRowHeight = true

    return table
  }
}