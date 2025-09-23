package io.confluent.kafka.core.monitoring.data.model

import io.confluent.kafka.core.table.renderers.DataRenderingUtil
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaField

/** Marker interface to mark types that are used in DataModel */
interface RemoteInfo {
  companion object {
    fun getProperties(obj: Any) =
      obj::class.declaredMemberProperties.filter { field ->
        DataRenderingUtil.shouldRenderFrom((field.annotations + (field.javaField?.annotations ?: emptyArray())).toTypedArray())
      }
  }
}