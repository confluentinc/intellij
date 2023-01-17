package com.jetbrains.bigdatatools.kafka.registry

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.json.JsonSchema
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema

object KafkaRegistryTemplates {
  private const val avroDefault =
    """{
  "type": "record",
  "name": "MyRecord",
  "namespace": "com.mycompany",
  "fields" : [
    {"name": "id", "type": "long"}
  ]
}"""

  private const val protobufSchema =
    """syntax = "proto3";

message MyRecord {
  int32 id = 1;
  string createdAt = 2;
  string name = 3;
}"""

  private const val jsonSchema = """{
  "${"$"}id": "https://mycompany.com/myrecord",
  "${"$"}schema": "https://json-schema.org/draft/2019-09/schema",
  "type": "object",
  "title": "MyRecord",
  "description": "Json schema for MyRecord",
  "properties": {
    "id": {
      "type": "string"
    },
    "name": {
      "type": [ "string", "null" ]
    }
  },
  "required": [ "id" ],
  "additionalProperties": false
}"""

  private val templates = mapOf(
    AvroSchema.TYPE to avroDefault,
    ProtobufSchema.TYPE to protobufSchema,
    JsonSchema.TYPE to jsonSchema
  )

  private fun getDefault(providerType: String) = templates[providerType] ?: error("Not found default for $providerType")

  fun getDefaultIfNotConfigured(prevText: String, newProvider: String): String? {
    val isDefault = prevText.isBlank() || prevText in templates.values
    return getDefault(newProvider).takeIf { isDefault }
  }
}