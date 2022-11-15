package com.jetbrains.bigdatatools.kafka.model

import com.jetbrains.bigdatatools.common.monitoring.data.model.RemoteInfo
import com.jetbrains.bigdatatools.common.table.renderers.DataRenderingUtil
import com.jetbrains.bigdatatools.common.table.renderers.NoRendering
import io.confluent.kafka.schemaregistry.client.SchemaMetadata
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaField

data class SchemaRegistryInfo(val name: String,
                              @NoRendering val meta: SchemaMetadata?) : RemoteInfo {
  val id = meta?.id ?: -1
  val version = meta?.version ?: -1
  val type = meta?.schemaType ?: ""
  val schema = meta?.schema ?: ""

  companion object {
    val renderableColumns: List<KProperty1<SchemaRegistryInfo, *>> by lazy {
      SchemaRegistryInfo::class.declaredMemberProperties.filter { DataRenderingUtil.shouldRenderFrom(it.javaField?.annotations) }
    }
  }
}