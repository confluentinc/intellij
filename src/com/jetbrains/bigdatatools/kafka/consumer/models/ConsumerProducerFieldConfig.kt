package com.jetbrains.bigdatatools.kafka.consumer.models

import com.amazonaws.services.schemaregistry.serializers.json.JsonDataWithSchema
import com.jetbrains.bigdatatools.kafka.common.models.FieldType
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryType
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchemaUtils
import io.confluent.kafka.schemaregistry.json.JsonSchema
import io.confluent.kafka.schemaregistry.json.JsonSchemaUtils
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaUtils
import java.util.*

data class ConsumerProducerFieldConfig(val type: FieldType,
                                       val valueText: String,
                                       val isKey: Boolean,
                                       val topic: String,

                                       val registryType: KafkaRegistryType,
                                       val schemaName: String,
                                       val parsedSchema: ParsedSchema?) {
  fun getValueObj() = when (type) {
    FieldType.STRING -> valueText
    FieldType.JSON -> valueText
    FieldType.LONG -> valueText.toLong()
    FieldType.DOUBLE -> valueText.toDouble()
    FieldType.FLOAT -> valueText.toFloat()
    FieldType.BASE64 -> Base64.getDecoder().decode(valueText)
    FieldType.NULL -> null
    FieldType.AVRO_REGISTRY -> AvroSchemaUtils.toObject(valueText, parsedSchema as AvroSchema)
    FieldType.PROTOBUF_REGISTRY -> ProtobufSchemaUtils.toObject(valueText, parsedSchema as ProtobufSchema)
    FieldType.JSON_REGISTRY -> {
      when (registryType) {
        KafkaRegistryType.NONE -> error("Not allowed")
        KafkaRegistryType.CONFLUENT -> JsonSchemaUtils.toObject(valueText, parsedSchema as JsonSchema)
        KafkaRegistryType.AWS_GLUE -> JsonDataWithSchema.builder(parsedSchema?.canonicalString(), valueText).build()
      }
    }
  }

}