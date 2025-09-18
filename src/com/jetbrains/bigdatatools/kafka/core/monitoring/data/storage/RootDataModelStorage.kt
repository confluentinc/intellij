package com.jetbrains.bigdatatools.kafka.core.monitoring.data.storage

import com.jetbrains.bigdatatools.kafka.core.monitoring.data.model.DataModel
import com.jetbrains.bigdatatools.kafka.core.monitoring.data.updater.BdtMonitoringUpdater

open class RootDataModelStorage(updater: BdtMonitoringUpdater, val models: List<DataModel<*>>) : DataModelStorage(updater) {
  init {
    init()
  }


  override fun getModelsForRefresh() = models
  override fun getAsMap(): Map<*, DataModel<*>> = mapOf<Any, DataModel<*>>()

  override fun clearSelected(list: List<Any?>) {}
}