package io.confluent.intellijplugin.registry.serde

import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
import io.confluent.kafka.schemaregistry.json.JsonSchema
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider

class BdtJsonSchemaProvider : JsonSchemaProvider() {
    override fun parseSchemaOrElseThrow(
        schemaString: String?,
        references: MutableList<SchemaReference>?,
        isNew: Boolean
    ): ParsedSchema =
        try {
            JsonSchema(
                schemaString,
                references,
                resolveReferences(references),
                null
            )
        } catch (e: Exception) {
            throw e
        }
}