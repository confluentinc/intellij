package io.confluent.intellijplugin.registry.serde

import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.client.rest.entities.Schema
import io.confluent.kafka.schemaregistry.json.JsonSchema
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider

class BdtJsonSchemaProvider(
    private val preResolvedRefs: Map<String, String>? = null
) : JsonSchemaProvider() {
    override fun parseSchemaOrElseThrow(
        schema: Schema,
        isNew: Boolean,
        normalize: Boolean
    ): ParsedSchema =
        try {
            JsonSchema(
                schema.schema,
                schema.references,
                preResolvedRefs ?: resolveReferences(schema),
                null
            )
        } catch (e: Exception) {
            throw e
        }
}