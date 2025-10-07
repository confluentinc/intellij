package io.confluent.intellijplugin.registry

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
  "${"$"}schema": "http://json-schema.org/draft-07/schema",
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
        KafkaRegistryFormat.AVRO to avroDefault,
        KafkaRegistryFormat.PROTOBUF to protobufSchema,
        KafkaRegistryFormat.JSON to jsonSchema
    )

    private fun getDefault(providerType: KafkaRegistryFormat) =
        templates[providerType] ?: error("Not found default for $providerType")

    fun getDefaultIfNotConfigured(prevText: String, newProvider: KafkaRegistryFormat): String? {
        val isDefault = prevText.isBlank() || prevText in templates.values
        return getDefault(newProvider).takeIf { isDefault }
    }
}