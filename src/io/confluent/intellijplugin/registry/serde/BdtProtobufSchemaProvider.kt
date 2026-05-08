package io.confluent.intellijplugin.registry.serde

import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.client.rest.entities.Schema
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaProvider

class BdtProtobufSchemaProvider(
    private val preResolvedRefs: Map<String, String>? = null
) : ProtobufSchemaProvider() {
    override fun parseSchemaOrElseThrow(
        schema: Schema,
        isNew: Boolean,
        normalize: Boolean
    ): ParsedSchema =
        try {
            ProtobufSchema(
                schema.schema,
                schema.references,
                preResolvedRefs ?: resolveReferences(schema),
                null,
                null
            )
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("${e.message}.\n${e.cause?.message}")
        } catch (e: Exception) {
            throw e
        }
}