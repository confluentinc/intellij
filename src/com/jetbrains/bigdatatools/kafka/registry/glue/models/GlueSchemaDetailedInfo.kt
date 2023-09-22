package com.jetbrains.bigdatatools.kafka.registry.glue.models

import com.jetbrains.bigdatatools.common.monitoring.data.model.RemoteInfo
import com.jetbrains.bigdatatools.common.table.renderers.DataRenderingUtil
import com.jetbrains.bigdatatools.kafka.util.KafkaLocalizedField
import software.amazon.awssdk.services.glue.model.GetSchemaResponse
import software.amazon.awssdk.services.glue.model.GetSchemaVersionResponse
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaField

data class GlueSchemaDetailedInfo(val schemaResponse: GetSchemaResponse,
                                  val versionResponse: GetSchemaVersionResponse,
                                  val tags: MutableMap<String, String>?) : RemoteInfo {
  companion object {
    val renderableColumns: List<KProperty1<GlueSchemaDetailedInfo, *>> by lazy {
      GlueSchemaDetailedInfo::class.declaredMemberProperties.filter { DataRenderingUtil.shouldRenderFrom(it.javaField?.annotations) }
    }

    val localizedField: List<KafkaLocalizedField<GlueSchemaDetailedInfo>> by lazy {
      listOf()
    }
  }
}