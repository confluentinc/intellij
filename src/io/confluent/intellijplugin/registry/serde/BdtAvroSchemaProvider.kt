package io.confluent.intellijplugin.registry.serde

import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider
import io.confluent.kafka.schemaregistry.client.rest.entities.Schema
import org.apache.avro.SchemaParseException

class BdtAvroSchemaProvider(
    private val preResolvedRefs: Map<String, String>? = null
) : AvroSchemaProvider() {
    override fun parseSchemaOrElseThrow(
        schema: Schema,
        isNew: Boolean,
        normalize: Boolean
    ): ParsedSchema = try {
        AvroSchema(schema.schema, schema.references, preResolvedRefs ?: resolveReferences(schema), null, isNew)
    } catch (e: SchemaParseException) {
        val message = e.message?.replace("<", "&lt;")?.replace(">", "&gt;")
        throw SchemaParseException(message)
    } catch (e: Exception) {
        throw e
    }
}