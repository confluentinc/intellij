package com.jetbrains.bigdatatools.kafka.model

import com.jetbrains.bigdatatools.common.monitoring.data.model.RemoteInfo
import com.jetbrains.bigdatatools.kafka.util.KafkaLocalizedField
import com.jetbrains.bigdatatools.kafka.util.generator.PrimitivesGenerator

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
    val renderableColumns: List<KafkaLocalizedField<SchemaRegistryFieldsInfo>> by lazy {
      listOf()
    }
  }
}