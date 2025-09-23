package io.confluent.kafka.common.models

import com.intellij.openapi.util.NlsSafe
import io.confluent.kafka.registry.KafkaRegistryFormat

data class RegistrySchemaInEditor(@NlsSafe val schemaName: String,
                                  val schemaFormat: KafkaRegistryFormat?) : Comparable<RegistrySchemaInEditor> {
  override fun compareTo(other: RegistrySchemaInEditor) = schemaName.compareTo(other.schemaName)

  override fun toString() = schemaName
}