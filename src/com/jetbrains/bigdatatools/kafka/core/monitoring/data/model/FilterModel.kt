package com.jetbrains.bigdatatools.kafka.core.monitoring.data.model

interface FilterModelChangeListener {
  fun filterChanged()
}

class FilterModel {

  private val listeners = mutableListOf<Pair<FilterModelChangeListener, FilterKey?>>()

  private val filters = mutableListOf<DataModelFilter>()

  /** Flag is used in beginBatch() endBatch() it will fire notifyFiltersChanged if needed. */
  private var dirtyKeys = mutableListOf<FilterKey>()

  private var batchMode = false

  fun beginBatch() {

    if(batchMode) {
      return
    }

    batchMode = true
    dirtyKeys.clear()
  }

  fun endBatch() {

    if(!batchMode) {
      return
    }

    if(dirtyKeys.isNotEmpty()) {
      for (listener in listeners) {
        if (listener.second != null && dirtyKeys.contains(listener.second!!)) {
          listener.first.filterChanged()
        }
        else {
          listener.first.filterChanged()
        }
      }
    }

    batchMode = false
  }

  /** You can subscribe only on certain key change or on any change if key is not specified */
  fun addSetFilterListener(listener: FilterModelChangeListener, key: FilterKey? = null) {
    listeners.add(Pair(listener, key))
  }

  fun removeSetFilterListener(listener: FilterModelChangeListener) {
    listeners.removeIf { it.first == listener }
  }

  fun removeFilter(filterKey: FilterKey) {
    if( filters.removeIf { it.key == filterKey } ) {
      notifyFiltersChanged(filterKey)
    }
  }

  fun setFilter(filter: DataModelFilter) {
    filters.removeIf { it.key == filter.key }
    filters.add(filter)
    notifyFiltersChanged(filter.key)
  }

  /** Filter key to value. ("limit" -> "20", "statesQuery" -> "RUNNING|STOPPED") */
  fun getFilters(): Map<String, String> {
    return filters.associate { it.key.name to it.stringValue }
  }

  private fun notifyFiltersChanged(filterKey: FilterKey) {

    if(batchMode) {
      dirtyKeys.add(filterKey)
      return
    }

    for (listener in listeners) {
      if (listener.second != null && listener.second == filterKey) {
        listener.first.filterChanged()
      }
      else {
        listener.first.filterChanged()
      }
    }
  }
}