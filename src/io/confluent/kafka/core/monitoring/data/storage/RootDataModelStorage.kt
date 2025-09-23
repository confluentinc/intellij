package io.confluent.kafka.core.monitoring.data.storage

import io.confluent.kafka.core.monitoring.data.model.DataModel
import io.confluent.kafka.core.monitoring.data.updater.BdtMonitoringUpdater

open class RootDataModelStorage(updater: BdtMonitoringUpdater, val models: List<DataModel<*>>) : DataModelStorage(updater) {
  init {
    init()
  }


  override fun getModelsForRefresh() = models
  override fun getAsMap(): Map<*, DataModel<*>> = mapOf<Any, DataModel<*>>()

  override fun clearSelected(list: List<Any?>) {}
}