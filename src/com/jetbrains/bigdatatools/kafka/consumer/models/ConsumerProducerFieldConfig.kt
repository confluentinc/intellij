package com.jetbrains.bigdatatools.kafka.consumer.models

import com.amazonaws.services.schemaregistry.serializers.json.JsonDataWithSchema
import com.jetbrains.bigdatatools.kafka.common.models.KafkaFieldType
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryFormat
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryType
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchemaUtils
import io.confluent.kafka.schemaregistry.json.JsonSchema
import io.confluent.kafka.schemaregistry.json.JsonSchemaUtils
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaUtils
import java.util.*

data class ConsumerProducerFieldConfig(val type: KafkaFieldType,
                                       val valueText: String,
                                       val isKey: Boolean,
                                       val topic: String,

                                       val registryType: KafkaRegistryType,
                                       val schemaName: String,
                                       val schemaFormat: KafkaRegistryFormat,
                                       val parsedSchema: ParsedSchema?) {
  fun getValueObj() = when (type) {
    KafkaFieldType.STRING -> valueText
    KafkaFieldType.JSON -> valueText
    KafkaFieldType.LONG -> valueText.toLong()
    KafkaFieldType.DOUBLE -> valueText.toDouble()
    KafkaFieldType.FLOAT -> valueText.toFloat()
    KafkaFieldType.BASE64 -> Base64.getDecoder().decode(valueText)
    KafkaFieldType.NULL -> null
    KafkaFieldType.SCHEMA_REGISTRY -> when (schemaFormat) {
      KafkaRegistryFormat.AVRO -> AvroSchemaUtils.toObject(valueText, parsedSchema as AvroSchema)
      KafkaRegistryFormat.PROTOBUF -> ProtobufSchemaUtils.toObject(valueText, parsedSchema as ProtobufSchema)
      KafkaRegistryFormat.JSON -> when (registryType) {
        KafkaRegistryType.NONE -> error("Not allowed")
        KafkaRegistryType.CONFLUENT -> JsonSchemaUtils.toObject(valueText, parsedSchema as JsonSchema)
        KafkaRegistryType.AWS_GLUE -> JsonDataWithSchema.builder(parsedSchema?.canonicalString(), valueText).build()
      }
      KafkaRegistryFormat.UNKNOWN -> {
        error("Schema is removed")
      }
    }
    KafkaFieldType.PROTOBUF_CUSTOM -> ProtobufSchemaUtils.toObject(valueText, parsedSchema as ProtobufSchema)
    KafkaFieldType.AVRO_CUSTOM -> AvroSchemaUtils.toObject(valueText, parsedSchema as AvroSchema)
  }
}