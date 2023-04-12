package com.jetbrains.bigdatatools.kafka.consumer.models

import com.jetbrains.bigdatatools.kafka.common.models.FieldType
import com.jetbrains.bigdatatools.kafka.common.models.RegistrySchemaInEditor
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryType

data class ConsumerProducerFieldConfig(val type: FieldType,
                                       val valueText: String,
                                       val isKey: Boolean,
                                       val topic: String,

                                       val registryType: KafkaRegistryType,
                                       private val rawSchemaName: String) {
  val schemaName = if (type in FieldType.registryValues) calculateSchemaName() else ""

  private fun calculateSchemaName() = if (rawSchemaName == RegistrySchemaInEditor.TOPIC_SCHEMA.schemaName) {
    val topicName = topic
    when (registryType) {
      KafkaRegistryType.NONE -> ""
      KafkaRegistryType.CONFLUENT -> if (isKey) "$topicName-key" else "$topicName-value"
      KafkaRegistryType.AWS_GLUE -> rawSchemaName
    }
  }
  else {
    rawSchemaName
  }

  fun getValueObj() = when (type) {
    FieldType.STRING -> valueText
    FieldType.JSON -> valueText
    FieldType.LONG -> valueText.toLong()
    FieldType.DOUBLE -> valueText.toDouble()
    FieldType.FLOAT -> valueText.toFloat()
    FieldType.BASE64 -> valueText
    FieldType.NULL -> null
    FieldType.AVRO_REGISTRY -> valueText
    FieldType.PROTOBUF_REGISTRY -> valueText
    FieldType.JSON_REGISTRY -> valueText
  }
}