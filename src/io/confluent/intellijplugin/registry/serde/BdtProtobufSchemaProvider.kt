package io.confluent.intellijplugin.registry.serde

import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.client.rest.entities.Schema
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaProvider

class BdtProtobufSchemaProvider : ProtobufSchemaProvider() {
    override fun parseSchemaOrElseThrow(
        schema: Schema,
        isNew: Boolean,
        normalize: Boolean
    ): ParsedSchema =
        try {
            ProtobufSchema(
                schema.schema,
                schema.references,
                resolveReferences(schema),
                null,
                null
            )
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("${e.message}.\n${e.cause?.message}")
        } catch (e: Exception) {
            throw e
        }
}