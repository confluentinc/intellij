package com.jetbrains.bigdatatools.kafka.util.generator

import com.google.gson.GsonBuilder
import com.intellij.openapi.diagnostic.Logger
import com.jetbrains.bigdatatools.kafka.common.models.FieldType
import com.jetbrains.bigdatatools.kafka.consumer.models.ConsumerProducerFieldConfig
import io.confluent.kafka.schemaregistry.ParsedSchema

object GenerateRandomData {
  val logger = Logger.getInstance(this::class.java)

  fun generate(config: ConsumerProducerFieldConfig): String = generate(config.type, config.parsedSchema)

  fun generate(fieldType: FieldType, parsedSchema: ParsedSchema?): String = when (fieldType) {
    FieldType.STRING -> PrimitivesGenerator.generateString()
    FieldType.LONG -> PrimitivesGenerator.generateLong().toString()
    FieldType.DOUBLE -> PrimitivesGenerator.generateDouble().toString()
    FieldType.FLOAT -> PrimitivesGenerator.generateFloat().toString()
    FieldType.BASE64 -> PrimitivesGenerator.generateBytes().toString()
    FieldType.AVRO_REGISTRY -> AvroGenerator.generateAvroMessage(parsedSchema)
    FieldType.JSON -> GsonBuilder().setPrettyPrinting().create().toJson(JsonGenerator.generateJson())
    FieldType.PROTOBUF_REGISTRY -> ProtobufGenerator.generateProtobufMessage(parsedSchema)
    else -> ""
  }
}