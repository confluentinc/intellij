package com.jetbrains.bigdatatools.kafka.model

import com.jetbrains.bigdatatools.core.monitoring.data.model.RemoteInfo
import com.jetbrains.bigdatatools.core.table.renderers.DataRenderingUtil
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaField

data class SchemaRegistryFieldsInfo(
  val name: String,
  val type: String,
  val default: String,
  val description: String,
  val required: String
) : RemoteInfo {
  override fun toString(): String = name

  companion object {
    val renderableColumns: List<KProperty1<SchemaRegistryFieldsInfo, *>> by lazy {
      SchemaRegistryFieldsInfo::class.declaredMemberProperties.filter { DataRenderingUtil.shouldRenderFrom(it.javaField?.annotations) }
    }
  }
}