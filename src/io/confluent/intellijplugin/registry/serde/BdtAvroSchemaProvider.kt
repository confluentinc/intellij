package io.confluent.intellijplugin.registry.serde

import io.confluent.intellijplugin.schemaregistry.ParsedSchema
import io.confluent.intellijplugin.schemaregistry.avro.AvroSchema
import io.confluent.intellijplugin.schemaregistry.avro.AvroSchemaProvider
import io.confluent.intellijplugin.schemaregistry.client.rest.entities.SchemaReference
import org.apache.avro.SchemaParseException

class BdtAvroSchemaProvider : AvroSchemaProvider() {
  override fun parseSchemaOrElseThrow(schemaString: String?,
                                      references: MutableList<SchemaReference>?,
                                      isNew: Boolean): ParsedSchema = try {
    AvroSchema(schemaString, references, resolveReferences(references), null, isNew)
  }
  catch (e: SchemaParseException) {
    val message = e.message?.replace("<", "&lt;")?.replace(">", "&gt;")
    throw SchemaParseException(message)
  }
  catch (e: Exception) {
    throw e
  }
}