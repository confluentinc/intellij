package com.jetbrains.bigdatatools.kafka.common.models

import com.intellij.openapi.util.NlsSafe
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryFormat

data class RegistrySchemaInEditor(@NlsSafe val schemaName: String,
                                  val schemaFormat: KafkaRegistryFormat) : Comparable<RegistrySchemaInEditor> {
  override fun compareTo(other: RegistrySchemaInEditor): Int {
    return schemaName.compareTo(other.schemaName)
  }

  override fun toString() = schemaName
}