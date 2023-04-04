package com.jetbrains.bigdatatools.kafka.registry.glue.models

import com.jetbrains.bigdatatools.common.monitoring.data.model.RemoteInfo
import com.jetbrains.bigdatatools.common.table.renderers.DataRenderingUtil
import com.jetbrains.bigdatatools.common.table.renderers.NoRendering
import software.amazon.awssdk.services.glue.model.SchemaId
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaField

data class GlueSchemaInfo(val schemaName: String,
                          val schemaArn: String,
                          val registryName: String,
                          val createdTime: String,
                          val description: String,
                          val schemaStatus: String,
                          val updatedTime: String) : RemoteInfo {
  @NoRendering
  val id = SchemaId.builder().schemaName(schemaName).registryName(registryName).build()

  companion object {
    val renderableColumns: List<KProperty1<GlueSchemaInfo, *>> by lazy {
      GlueSchemaInfo::class.declaredMemberProperties.filter { DataRenderingUtil.shouldRenderFrom(it.javaField?.annotations) }
    }
  }
}