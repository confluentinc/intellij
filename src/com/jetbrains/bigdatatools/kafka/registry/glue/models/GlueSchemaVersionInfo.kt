package com.jetbrains.bigdatatools.kafka.registry.glue.models

import com.jetbrains.bigdatatools.common.monitoring.data.model.RemoteInfo
import com.jetbrains.bigdatatools.common.table.renderers.DataRenderingUtil
import com.jetbrains.bigdatatools.common.table.renderers.NoRendering
import software.amazon.awssdk.services.glue.model.SchemaId
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaField

data class GlueSchemaVersionInfo(
  val version: Long,
  val registered: String,
  val status: String,
  @NoRendering val schemaId: SchemaId,
) : RemoteInfo {
  companion object {
    val renderableColumns: List<KProperty1<GlueSchemaVersionInfo, *>> by lazy {
      GlueSchemaVersionInfo::class.declaredMemberProperties.filter { DataRenderingUtil.shouldRenderFrom(it.javaField?.annotations) }
    }
  }
}