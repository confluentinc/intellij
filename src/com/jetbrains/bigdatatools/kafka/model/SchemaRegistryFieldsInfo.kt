package com.jetbrains.bigdatatools.kafka.model

import com.jetbrains.bigdatatools.common.monitoring.data.model.RemoteInfo
import com.jetbrains.bigdatatools.common.table.renderers.DataRenderingUtil
import com.jetbrains.bigdatatools.kafka.util.KafkaLocalizedField
import com.jetbrains.bigdatatools.kafka.util.generator.PrimitivesGenerator
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
  val id = "$name${PrimitivesGenerator.generateLong()}"

  override fun toString(): String = name

  companion object {
    val renderableColumns: List<KProperty1<SchemaRegistryFieldsInfo, *>> by lazy {
      SchemaRegistryFieldsInfo::class.declaredMemberProperties.filter { DataRenderingUtil.shouldRenderFrom(it.javaField?.annotations) }
    }

    val localizedField: List<KafkaLocalizedField<SchemaRegistryFieldsInfo>> by lazy {
      listOf()
    }
  }
}