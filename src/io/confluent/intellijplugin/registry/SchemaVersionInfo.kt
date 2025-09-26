package io.confluent.intellijplugin.registry

import io.confluent.intellijplugin.schemaregistry.client.rest.entities.SchemaReference

data class SchemaVersionInfo(val schemaName: String,
                             val version: Long,
                             val type: KafkaRegistryFormat,
                             val schema: String,
                             val references: List<SchemaReference> = emptyList()) {
  fun getPretty() = KafkaRegistryUtil.getPrettySchema(schemaType = type.name, schema = schema)
}