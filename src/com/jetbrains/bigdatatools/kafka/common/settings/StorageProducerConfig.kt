package io.confluent.kafka.common.settings

import io.confluent.kafka.common.models.KafkaFieldType
import io.confluent.kafka.consumer.models.CustomSchemaData
import io.confluent.kafka.core.settings.connections.Property
import io.confluent.kafka.producer.models.AcksType
import io.confluent.kafka.producer.models.ProducerFlowParams
import io.confluent.kafka.producer.models.RecordCompression
import io.confluent.kafka.registry.ConfluentRegistryStrategy
import io.confluent.kafka.registry.KafkaRegistryFormat

data class StorageProducerConfig(
  var topic: String = "",
  var keyType: String = "",
  var key: String = "",
  var keyFormat: String = "",
  var valueType: String = "",
  var valueFormat: String = "",
  var value: String = "",
  var properties: List<Property> = emptyList(),
  var compression: String = "",
  var acks: String = "",
  var idempotence: Boolean = false,
  var forcePartition: Int = -1,
  var keyStrategy: ConfluentRegistryStrategy = ConfluentRegistryStrategy.TOPIC_NAME,
  var valueStrategy: ConfluentRegistryStrategy = ConfluentRegistryStrategy.TOPIC_NAME,
  var keySubject: String = "",
  var valueSubject: String = "",
  var flowParams: ProducerFlowParams? = null,

  var customKeySchema: CustomSchemaData? = null,
  var customValueSchema: CustomSchemaData? = null,
) : StorageConfig {
  fun takeKeyType(): KafkaFieldType = KafkaFieldType.entries.find { it.name == keyType } ?: KafkaFieldType.SCHEMA_REGISTRY
  fun takeValueType(): KafkaFieldType = KafkaFieldType.entries.find { it.name == valueType } ?: KafkaFieldType.SCHEMA_REGISTRY
  fun takeKeyFormat(): KafkaRegistryFormat = KafkaRegistryFormat.entries.find { it.name == keyFormat } ?: KafkaRegistryFormat.AVRO
  fun takeValueFormat(): KafkaRegistryFormat = KafkaRegistryFormat.entries.find { it.name == valueFormat } ?: KafkaRegistryFormat.AVRO
  fun getCompression(): RecordCompression = RecordCompression.entries.find { it.name == compression } ?: RecordCompression.NONE
  fun getAsks(): AcksType = AcksType.entries.find { it.name == acks } ?: AcksType.NONE
}