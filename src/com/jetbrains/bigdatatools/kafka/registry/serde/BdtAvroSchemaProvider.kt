package com.jetbrains.bigdatatools.kafka.registry.serde

import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
import org.apache.avro.SchemaParseException

class BdtAvroSchemaProvider : AvroSchemaProvider() {
  override fun parseSchemaOrElseThrow(schemaString: String?,
                                      references: MutableList<SchemaReference>?,
                                      isNew: Boolean): ParsedSchema = try {
    super.parseSchemaOrElseThrow(schemaString, references, isNew)
  }
  catch (e: SchemaParseException) {
    val message = e.message?.replace("<", "&lt;")?.replace(">", "&gt;")
    throw SchemaParseException(message)
  }
}