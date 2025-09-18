package com.jetbrains.bigdatatools.kafka.core.monitoring.toolwindow

import com.jetbrains.bigdatatools.kafka.core.monitoring.data.model.RemoteInfo
import com.jetbrains.bigdatatools.kafka.core.monitoring.table.extension.TableColumnsFitter
import com.jetbrains.bigdatatools.kafka.core.monitoring.table.extension.TableExtensionType
import com.jetbrains.bigdatatools.kafka.core.monitoring.table.extension.TableLoadingDecorator
import java.util.*

abstract class DetailsTableMonitoringController<T : RemoteInfo, ID> : AbstractTableController<T>(), DetailsMonitoringController<ID> {
  protected var selectedId: ID? = null

  override fun getTableExtensions(): EnumSet<TableExtensionType> =
    EnumSet.copyOf(super.getTableExtensions() - TableExtensionType.LOADING_INDICATOR)

  override fun setDetailsId(id: ID) {
    selectedId = id

    val model = getDataModel() ?: return
    dataTable.tableModel.setDataModel(model)

    TableColumnsFitter.get(dataTable)?.reset()
    TableLoadingDecorator.installOn(dataTable)

    decoratedTableComponent.revalidate()
    decoratedTableComponent.repaint()
  }
}