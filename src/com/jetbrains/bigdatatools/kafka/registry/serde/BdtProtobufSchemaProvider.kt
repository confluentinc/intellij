package com.jetbrains.bigdatatools.kafka.registry.serde

import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaProvider

class BdtProtobufSchemaProvider : ProtobufSchemaProvider() {
  override fun parseSchemaOrElseThrow(schemaString: String?,
                                      references: MutableList<SchemaReference>?,
                                      isNew: Boolean): ParsedSchema =
    try {
      ProtobufSchema(
        schemaString,
        references,
        resolveReferences(references),
        null,
        null
      )
    }
    catch (e: Exception) {
      throw e
    }
}