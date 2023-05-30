package com.jetbrains.bigdatatools.kafka.util.generator

import com.google.gson.GsonBuilder
import com.intellij.openapi.diagnostic.Logger
import com.jetbrains.bigdatatools.kafka.common.models.KafkaFieldType
import com.jetbrains.bigdatatools.kafka.consumer.models.ConsumerProducerFieldConfig
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryFormat
import io.confluent.kafka.schemaregistry.ParsedSchema

object GenerateRandomData {
  val logger = Logger.getInstance(this::class.java)

  fun generate(config: ConsumerProducerFieldConfig): String = generate(config.type, config.parsedSchema)

  fun generate(fieldType: KafkaFieldType, parsedSchema: ParsedSchema?): String = when (fieldType) {
    KafkaFieldType.STRING -> PrimitivesGenerator.generateString()
    KafkaFieldType.LONG -> PrimitivesGenerator.generateLong().toString()
    KafkaFieldType.DOUBLE -> PrimitivesGenerator.generateDouble().toString()
    KafkaFieldType.FLOAT -> PrimitivesGenerator.generateFloat().toString()
    KafkaFieldType.BASE64 -> PrimitivesGenerator.generateBytesBase64().toString()
    KafkaFieldType.SCHEMA_REGISTRY -> {
      val schemaType = parsedSchema?.schemaType()
      val format = KafkaRegistryFormat.parse(schemaType ?: error("Schema is not provided for generation data"))
      when (format) {
        KafkaRegistryFormat.AVRO -> AvroGenerator.generateAvroMessage(parsedSchema)
        KafkaRegistryFormat.PROTOBUF -> ProtobufGenerator.generateProtobufMessage(parsedSchema)
        KafkaRegistryFormat.JSON -> ""
        KafkaRegistryFormat.UNKNOWN -> error("Schema is unknown for $parsedSchema")
      }
    }
    KafkaFieldType.JSON -> GsonBuilder().setPrettyPrinting().create().toJson(JsonGenerator.generateJson())
    KafkaFieldType.NULL -> ""
  }
}