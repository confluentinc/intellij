package com.jetbrains.bigdatatools.kafka.registry.common

import com.jetbrains.bigdatatools.common.monitoring.data.model.RemoteInfo
import com.jetbrains.bigdatatools.common.table.renderers.DataRenderingUtil
import com.jetbrains.bigdatatools.common.table.renderers.DateRendering
import com.jetbrains.bigdatatools.common.table.renderers.LoadingRendering
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryFormat
import java.util.*
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaField

data class KafkaSchemaInfo(val name: String,
                           @field:LoadingRendering
                           val type: KafkaRegistryFormat? = null,
                           @field:LoadingRendering
                           val versions: Long? = null,
                           @field:LoadingRendering
                           val compatibility: String? = null,
                           @field:DateRendering
                           val updatedTime: Date? = null,
                           @field:LoadingRendering
                           val description: String? = null,
                           @field:LoadingRendering
                           val schemaStatus: String? = null,
                           val isSoftDeleted: Boolean = versions == -1L) : RemoteInfo {
  companion object {
    val renderableColumns: List<KProperty1<KafkaSchemaInfo, *>> by lazy {
      KafkaSchemaInfo::class.declaredMemberProperties.filter { DataRenderingUtil.shouldRenderFrom(it.javaField?.annotations) }
    }
  }
}