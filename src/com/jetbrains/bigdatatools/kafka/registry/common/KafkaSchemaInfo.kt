package com.jetbrains.bigdatatools.kafka.registry.common

import com.jetbrains.bigdatatools.common.monitoring.data.model.RemoteInfo
import com.jetbrains.bigdatatools.common.table.renderers.CustomRendering
import com.jetbrains.bigdatatools.common.table.renderers.DataRenderingUtil
import com.jetbrains.bigdatatools.common.table.renderers.DateRendering
import com.jetbrains.bigdatatools.common.table.renderers.LoadingRendering
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryFormat
import com.jetbrains.bigdatatools.kafka.util.RegistryFormatRenderer
import java.util.*
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaField

data class KafkaSchemaInfo(val name: String,
                           @field:CustomRendering(RegistryFormatRenderer::class)
                           val type: KafkaRegistryFormat? = null,
                           @field:LoadingRendering
                           val version: Long? = null,
                           @field:LoadingRendering
                           val compatibility: String? = null,
                           @field:DateRendering
                           val updatedTime: Date? = null,
                           @field:LoadingRendering
                           val description: String? = null,
                           @field:LoadingRendering
                           val schemaStatus: String? = null,
                           val isSoftDeleted: Boolean = version == -1L,
                           val isFavorite: Boolean = false) : RemoteInfo {
  companion object {
    val renderableColumns: List<KProperty1<KafkaSchemaInfo, *>> by lazy {
      KafkaSchemaInfo::class.declaredMemberProperties.filter { DataRenderingUtil.shouldRenderFrom(it.javaField?.annotations) }
    }

    fun createEmpty(name: String) = KafkaSchemaInfo(
      name = name,
      type = KafkaRegistryFormat.UNKNOWN,
      version = -1,
      compatibility = "",
      description = "",
      schemaStatus = ""
    )
  }
}