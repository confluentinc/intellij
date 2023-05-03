package com.jetbrains.bigdatatools.kafka.util.generator

import com.jetbrains.bigdatatools.kafka.common.models.FieldType
import com.jetbrains.bigdatatools.kafka.consumer.models.ConsumerProducerFieldConfig
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager

object GenerateRandomData {
  fun generate(config: ConsumerProducerFieldConfig, dataManager: KafkaDataManager): String = when (config.type) {
    FieldType.STRING -> PrimitivesGenerator.generateString()
    FieldType.LONG -> PrimitivesGenerator.generateLong()
    FieldType.DOUBLE -> PrimitivesGenerator.generateDouble()
    FieldType.FLOAT -> PrimitivesGenerator.generateFloat()
    FieldType.BASE64 -> PrimitivesGenerator.generateBytes()
    FieldType.AVRO_REGISTRY -> AvroGenerator.generateAvroMessage(config, dataManager)
    else -> ""
  }
}