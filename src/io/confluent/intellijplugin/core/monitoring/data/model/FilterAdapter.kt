package io.confluent.intellijplugin.core.monitoring.data.model

import io.confluent.intellijplugin.core.monitoring.table.model.DataTableModel
import io.confluent.intellijplugin.core.ui.filter.CountFilterListener
import io.confluent.intellijplugin.core.ui.filter.CountFilterPopupComponent

/** Adapts different filter components to update selected filter model. */
object FilterAdapter {
    fun updateFilter(filters: FilterModel, filterKey: FilterKey, value: Int?) {
        if (value == null) {
            filters.removeFilter(filterKey)
        } else {
            filters.setFilter(DataModelFilter(filterKey, value.toString()))
        }
    }

    fun <T : RemoteInfo> install(
        tableModel: DataTableModel<T>,
        counterFilter: CountFilterPopupComponent,
        filterKey: FilterKey,
        onChanged: (Int?) -> Unit
    ) {

        counterFilter.addListener(object : CountFilterListener {
            override fun filterChanged(value: Int?) {
                val filters = tableModel.getDataModel()?.filters ?: return
                updateFilter(filters, filterKey, value)
                onChanged(value)
            }
        })
    }
}