package com.jetbrains.bigdatatools.kafka.common.settings

import com.jetbrains.bigdatatools.common.settings.connections.Property
import com.jetbrains.bigdatatools.kafka.common.models.KafkaFieldType
import com.jetbrains.bigdatatools.kafka.producer.models.AcksType
import com.jetbrains.bigdatatools.kafka.producer.models.ProducerFlowParams
import com.jetbrains.bigdatatools.kafka.producer.models.RecordCompression
import com.jetbrains.bigdatatools.kafka.registry.ConfluentRegistryStrategy
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryFormat

data class StorageProducerConfig(var topic: String = "",
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
                                 var flowParams: ProducerFlowParams? = null) : StorageConfig {


  fun takeKeyType(): KafkaFieldType = KafkaFieldType.values().find { it.name == keyType } ?: KafkaFieldType.SCHEMA_REGISTRY
  fun takeValueType(): KafkaFieldType = KafkaFieldType.values().find { it.name == valueType } ?: KafkaFieldType.SCHEMA_REGISTRY
  fun takeKeyFormat(): KafkaRegistryFormat = KafkaRegistryFormat.values().find { it.name == keyFormat } ?: KafkaRegistryFormat.AVRO
  fun takeValueFormat(): KafkaRegistryFormat = KafkaRegistryFormat.values().find { it.name == valueFormat } ?: KafkaRegistryFormat.AVRO
  fun getCompression(): RecordCompression = RecordCompression.values().find { it.name == compression } ?: RecordCompression.NONE
  fun getAsks(): AcksType = AcksType.values().find { it.name == acks } ?: AcksType.NONE
}