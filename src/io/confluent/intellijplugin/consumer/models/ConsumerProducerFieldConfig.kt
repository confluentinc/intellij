package io.confluent.intellijplugin.consumer.models

import com.amazonaws.services.schemaregistry.serializers.json.JsonDataWithSchema
import io.confluent.intellijplugin.common.models.KafkaFieldType
import io.confluent.intellijplugin.registry.KafkaRegistryFormat
import io.confluent.intellijplugin.registry.KafkaRegistryType
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchemaUtils
import io.confluent.kafka.schemaregistry.json.JsonSchema
import io.confluent.kafka.schemaregistry.json.JsonSchemaUtils
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaUtils
import java.util.*

data class ConsumerProducerFieldConfig(
    val type: KafkaFieldType,
    val valueText: String,
    val isKey: Boolean,
    val topic: String,

    val registryType: KafkaRegistryType,
    val schemaName: String,
    val schemaFormat: KafkaRegistryFormat,
    val parsedSchema: ParsedSchema?
) {
    fun getValueObj() = when (type) {
        KafkaFieldType.STRING -> valueText.ifEmpty { null }
        KafkaFieldType.JSON -> valueText
        KafkaFieldType.LONG -> valueText.toLong()
        KafkaFieldType.INTEGER -> valueText.toInt()
        KafkaFieldType.DOUBLE -> valueText.toDouble()
        KafkaFieldType.FLOAT -> valueText.toFloat()
        KafkaFieldType.BASE64 -> Base64.getDecoder().decode(valueText)
        KafkaFieldType.NULL -> null
        KafkaFieldType.SCHEMA_REGISTRY -> {
            if (valueText.isBlank())
                throw Exception(KafkaMessagesBundle.message("validator.notEmpty"))
            when (schemaFormat) {
                KafkaRegistryFormat.AVRO -> AvroSchemaUtils.toObject(valueText, parsedSchema as AvroSchema)
                KafkaRegistryFormat.PROTOBUF -> ProtobufSchemaUtils.toObject(valueText, parsedSchema as ProtobufSchema)
                KafkaRegistryFormat.JSON -> when (registryType) {
                    KafkaRegistryType.NONE -> error("Not allowed")
                    KafkaRegistryType.CONFLUENT -> JsonSchemaUtils.toObject(valueText, parsedSchema as JsonSchema)
                    KafkaRegistryType.AWS_GLUE -> JsonDataWithSchema.builder(parsedSchema?.canonicalString(), valueText)
                        .build()
                }

                KafkaRegistryFormat.UNKNOWN -> {
                    error("Schema is removed")
                }
            }
        }

        KafkaFieldType.PROTOBUF_CUSTOM -> ProtobufSchemaUtils.toObject(valueText, parsedSchema as ProtobufSchema)
        KafkaFieldType.AVRO_CUSTOM -> AvroSchemaUtils.toObject(valueText, parsedSchema as AvroSchema)
    }
}