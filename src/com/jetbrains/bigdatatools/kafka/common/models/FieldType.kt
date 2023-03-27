package com.jetbrains.bigdatatools.kafka.common.models

import com.amazonaws.services.schemaregistry.deserializers.GlueSchemaRegistryKafkaDeserializer
import com.amazonaws.services.schemaregistry.serializers.GlueSchemaRegistryKafkaSerializer
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryType
import com.jetbrains.bigdatatools.kafka.registry.serde.BdtKafkaAvroDeserializer
import com.jetbrains.bigdatatools.kafka.registry.serde.BdtKafkaJsonSchemaDeserializer
import com.jetbrains.bigdatatools.kafka.registry.serde.BdtKafkaProtobufDeserializer
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.confluent.kafka.serializers.json.KafkaJsonSchemaSerializer
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer
import org.apache.kafka.common.serialization.*
import org.jetbrains.annotations.Nls

enum class FieldType(@Nls val title: String) {
  JSON(KafkaMessagesBundle.message("field.type.json")),
  STRING(KafkaMessagesBundle.message("field.type.string")),
  LONG(KafkaMessagesBundle.message("field.type.long")),
  DOUBLE(KafkaMessagesBundle.message("field.type.double")),
  FLOAT(KafkaMessagesBundle.message("field.type.float")),
  BASE64(KafkaMessagesBundle.message("field.type.base64")),
  NULL(KafkaMessagesBundle.message("field.type.null")),

  AVRO_REGISTRY(KafkaMessagesBundle.message("field.type.avro.registry")),
  PROTOBUF_REGISTRY(KafkaMessagesBundle.message("field.type.protobuf.registry")),
  JSON_REGISTRY(KafkaMessagesBundle.message("field.type.json.registry"));


  fun getDeserializationClass(registryType: KafkaRegistryType) = when (this) {
    STRING, JSON -> StringDeserializer()
    LONG -> LongDeserializer()
    DOUBLE -> DoubleDeserializer()
    FLOAT -> FloatDeserializer()
    BASE64 -> ByteArrayDeserializer()
    NULL -> VoidDeserializer()
    AVRO_REGISTRY -> if (registryType == KafkaRegistryType.AWS_GLUE)
      GlueSchemaRegistryKafkaDeserializer()
    else
      BdtKafkaAvroDeserializer()
    PROTOBUF_REGISTRY -> if (registryType == KafkaRegistryType.AWS_GLUE)
      GlueSchemaRegistryKafkaDeserializer()
    else
      BdtKafkaProtobufDeserializer()

    JSON_REGISTRY -> if (registryType == KafkaRegistryType.AWS_GLUE)
      GlueSchemaRegistryKafkaDeserializer()
    else
      BdtKafkaJsonSchemaDeserializer()

  }

  fun getSerializer(registryClient: SchemaRegistryClient? = null) = when (this) {
    STRING -> StringSerializer()
    JSON -> StringSerializer()
    LONG -> LongSerializer()
    DOUBLE -> DoubleSerializer()
    FLOAT -> FloatSerializer()
    BASE64 -> ByteArraySerializer()
    NULL -> VoidSerializer()
    AVRO_REGISTRY -> if (registryClient != null) KafkaAvroSerializer(registryClient) else GlueSchemaRegistryKafkaSerializer()
    PROTOBUF_REGISTRY -> if (registryClient != null) KafkaProtobufSerializer(registryClient) else GlueSchemaRegistryKafkaSerializer()
    JSON_REGISTRY -> if (registryClient != null) KafkaJsonSchemaSerializer() else GlueSchemaRegistryKafkaSerializer()
  }

  companion object {
    val defaultValues = listOf(JSON, STRING, LONG, DOUBLE, FLOAT, BASE64, NULL)
    val registryValues = listOf(AVRO_REGISTRY, PROTOBUF_REGISTRY, JSON_REGISTRY)

    const val KEY_PARSED_SCHEMA_CONFIG_KEY = "bdt.registry.key.schema.custom"
    const val VALUE_PARSED_SCHEMA_CONFIG_KEY = "bdt.registry.value.schema.custom"
  }
}