package com.jetbrains.bigdatatools.kafka.data

import com.jetbrains.bigdatatools.monitoring.data.model.ObjectDataModel
import com.jetbrains.bigdatatools.monitoring.data.model.RemoteInfo
import kotlin.reflect.KClass

class ProjectionObjectDataModel<T : RemoteInfo>(forClass: KClass<T>,
                                                idField: String,
                                                val getData: () -> List<T>) : ObjectDataModel<T>(forClass) {
  init {
    updateData()
  }

  override val idFieldName: String = idField

  fun updateData() {
    val newData = try {
      getData()
    }
    catch (t: Throwable) {
      setError("Update Error", t)
      return
    }
    if (newData != data)
      setData(newData)
  }
}