package com.jetbrains.bigdatatools.kafka.consumer.models

import com.jetbrains.bigdatatools.kafka.common.models.FieldType
import com.jetbrains.bigdatatools.kafka.common.models.RegistrySchemaInEditor
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryType

data class ConsumerProducerFieldConfig(val type: FieldType,
                                       val valueText: String,
                                       val isKey: Boolean,
                                       val topic: String,

                                       val registryType: KafkaRegistryType,
                                       private val rawSchemaName: String,
                                       val registryName: String) {
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
}