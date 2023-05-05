package com.jetbrains.bigdatatools.kafka.util.generator

import com.jetbrains.bigdatatools.kafka.common.models.FieldType
import com.jetbrains.bigdatatools.kafka.consumer.models.ConsumerProducerFieldConfig
import io.confluent.kafka.schemaregistry.ParsedSchema
import java.util.*

object GenerateRandomData {
  fun generate(config: ConsumerProducerFieldConfig): String = generate(config.type, config.parsedSchema)

  fun generate(fieldType: FieldType, parsedSchema: ParsedSchema?): String = when (fieldType) {
    FieldType.STRING -> PrimitivesGenerator.generateString()
    FieldType.LONG -> PrimitivesGenerator.generateLong().toString()
    FieldType.DOUBLE -> PrimitivesGenerator.generateDouble().toString()
    FieldType.FLOAT -> PrimitivesGenerator.generateFloat().toString()
    FieldType.BASE64 -> Base64.getEncoder().encodeToString(PrimitivesGenerator.generateBytes())
    FieldType.AVRO_REGISTRY -> AvroGenerator.generateAvroMessage(parsedSchema)
    else -> ""
  }
}