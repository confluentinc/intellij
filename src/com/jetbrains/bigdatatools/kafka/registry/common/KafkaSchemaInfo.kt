package com.jetbrains.bigdatatools.kafka.registry.common

import com.jetbrains.bigdatatools.common.monitoring.data.model.RemoteInfo
import com.jetbrains.bigdatatools.common.table.renderers.CustomRendering
import com.jetbrains.bigdatatools.common.table.renderers.DateRendering
import com.jetbrains.bigdatatools.common.table.renderers.LoadingRendering
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryFormat
import com.jetbrains.bigdatatools.kafka.util.KafkaLocalizedField
import com.jetbrains.bigdatatools.kafka.util.RegistryFormatRenderer
import java.util.*

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
    val renderableColumns: List<KafkaLocalizedField<KafkaSchemaInfo>> by lazy {
      listOf(
        KafkaLocalizedField(KafkaSchemaInfo::name, "data.KafkaSchemaInfo.name"),
        KafkaLocalizedField(KafkaSchemaInfo::type, "data.KafkaSchemaInfo.type"),
        KafkaLocalizedField(KafkaSchemaInfo::version, "data.KafkaSchemaInfo.version"),
        KafkaLocalizedField(KafkaSchemaInfo::compatibility, "data.KafkaSchemaInfo.compatibility"),
        KafkaLocalizedField(KafkaSchemaInfo::updatedTime, "data.KafkaSchemaInfo.updatedTime"),
        KafkaLocalizedField(KafkaSchemaInfo::description, "data.KafkaSchemaInfo.description"),
        KafkaLocalizedField(KafkaSchemaInfo::schemaStatus, "data.KafkaSchemaInfo.schemaStatus"),
        KafkaLocalizedField(KafkaSchemaInfo::isSoftDeleted, "data.KafkaSchemaInfo.isSoftDeleted"),
        KafkaLocalizedField(KafkaSchemaInfo::isFavorite, i18Key = null)
      )
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