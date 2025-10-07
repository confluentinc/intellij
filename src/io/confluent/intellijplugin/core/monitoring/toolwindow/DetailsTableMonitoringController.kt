package io.confluent.intellijplugin.core.monitoring.toolwindow

import io.confluent.intellijplugin.core.monitoring.data.model.RemoteInfo
import io.confluent.intellijplugin.core.monitoring.table.extension.TableColumnsFitter
import io.confluent.intellijplugin.core.monitoring.table.extension.TableExtensionType
import io.confluent.intellijplugin.core.monitoring.table.extension.TableLoadingDecorator
import java.util.*

abstract class DetailsTableMonitoringController<T : RemoteInfo, ID> : AbstractTableController<T>(),
    DetailsMonitoringController<ID> {
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