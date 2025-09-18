package com.jetbrains.bigdatatools.kafka.core.monitoring.data.storage

import com.intellij.openapi.util.Disposer
import com.jetbrains.bigdatatools.kafka.core.monitoring.data.model.DataModel
import com.jetbrains.bigdatatools.kafka.core.monitoring.data.model.ObjectDataModel
import com.jetbrains.bigdatatools.kafka.core.monitoring.data.model.RemoteInfo
import com.jetbrains.bigdatatools.kafka.core.monitoring.data.updater.BdtMonitoringUpdater
import kotlin.reflect.KProperty1

class ObjectDataModelStorage<KEY_TYPE,
  MODEL_TYPE : RemoteInfo>(updater: BdtMonitoringUpdater,
                           private val idFieldName: KProperty1<MODEL_TYPE, Any?>,
                           val dependOn: DataModel<*>? = null,
                           val updateFun: (key: KEY_TYPE) -> List<MODEL_TYPE>) : DataModelStorage(updater) {
  private val models = mutableMapOf<KEY_TYPE, ObjectDataModel<MODEL_TYPE>>()

  init {
    init()
  }

  operator fun get(key: KEY_TYPE): ObjectDataModel<MODEL_TYPE> = synchronized(this) {
    models.getOrPut(key) {
      ObjectDataModel(idFieldName, allowAutoRefresh = dependOn == null) {
        updateFun(key) to false
      }.also { model ->
        Disposer.register(this, model)
        updater.invokeRefreshModel(model)
        dependOn?.let { model.subscribeOn(it) }
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