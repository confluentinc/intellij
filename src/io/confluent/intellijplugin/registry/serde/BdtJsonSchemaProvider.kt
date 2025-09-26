package io.confluent.intellijplugin.registry.serde

import io.confluent.intellijplugin.schemaregistry.ParsedSchema
import io.confluent.intellijplugin.schemaregistry.client.rest.entities.SchemaReference
import io.confluent.intellijplugin.schemaregistry.json.JsonSchema
import io.confluent.intellijplugin.schemaregistry.json.JsonSchemaProvider

class BdtJsonSchemaProvider : JsonSchemaProvider() {
  override fun parseSchemaOrElseThrow(schemaString: String?,
                                      references: MutableList<SchemaReference>?,
                                      isNew: Boolean): ParsedSchema =
    try {
      JsonSchema(
        schemaString,
        references,
        resolveReferences(references),
        null
      )
    }
    catch (e: Exception) {
      throw e
    }
}