package io.confluent.kafka.core.monitoring.data.storage

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.util.Disposer
import io.confluent.kafka.core.monitoring.data.model.DataModel
import io.confluent.kafka.core.monitoring.data.model.FieldGroupsData
import io.confluent.kafka.core.monitoring.data.model.FieldsGroupModel
import io.confluent.kafka.core.monitoring.data.updater.BdtMonitoringUpdater

open class FieldGroupsDataModelStorage<KEY_TYPE, OBJECT_TYPE>(updater: BdtMonitoringUpdater,
                                                              private val dependOn: DataModel<*>? = null,
                                                              val updateFun: (key: KEY_TYPE) -> FieldGroupsData<OBJECT_TYPE>) : DataModelStorage(
  updater) {
  private val models = ConcurrentCollectionFactory.createConcurrentMap<KEY_TYPE, FieldsGroupModel<OBJECT_TYPE>>()

  init {
    init()
  }

  open operator fun get(key: KEY_TYPE): FieldsGroupModel<OBJECT_TYPE> = synchronized(this) {
    models.getOrPut(key) {
      FieldsGroupModel(allowAutoRefresh = dependOn == null) {
        updateFun(key)
      }.also { model ->
        Disposer.register(this, model)
        dependOn?.let { model.subscribeOn(it) }
        updater.invokeRefreshModel(model)
      }
    }
  }

  override fun getModelsForRefresh() = synchronized(this) {
    models.map { it.value }
  }

  override fun getAsMap(): Map<*, DataModel<*>> = models

  override fun clearSelected(list: List<Any?>) = synchronized(this) {
    list.forEach {
      @Suppress("UNCHECKED_CAST")
      models.remove(it as KEY_TYPE)
    }
  }
}