package com.jetbrains.bigdatatools.kafka.common.settings

import com.jetbrains.bigdatatools.common.settings.connections.Property
import com.jetbrains.bigdatatools.kafka.common.models.FieldType
import com.jetbrains.bigdatatools.kafka.producer.models.AcksType
import com.jetbrains.bigdatatools.kafka.producer.models.RecordCompression
import com.jetbrains.bigdatatools.kafka.registry.ConfluentRegistryStrategy

data class StorageProducerConfig(var topic: String = "",
                                 var keyType: String = "",
                                 var key: String = "",
                                 var valueType: String = "",
                                 var value: String = "",
                                 var properties: List<Property> = emptyList(),
                                 var compression: String = "",
                                 var acks: String = "",
                                 var idempotence: Boolean = false,
                                 var forcePartition: Int = -1,
                                 var keyStrategy: ConfluentRegistryStrategy = ConfluentRegistryStrategy.TOPIC_NAME,
                                 var valueStrategy: ConfluentRegistryStrategy = ConfluentRegistryStrategy.TOPIC_NAME,
                                 val keySubject: String = "",
                                 val keyRegistry: String = "",
                                 val valueSubject: String = "",
                                 val valueRegistry: String = "") : StorageConfig {


  constructor(topic: String,
              keyType: FieldType,
              key: String,
              valueType: FieldType,
              value: String,
              properties: List<Property>,
              compression: RecordCompression,
              acks: AcksType,
              idempotence: Boolean,
              forcePartition: Int,
              keyStrategy: ConfluentRegistryStrategy,
              valueStrategy: ConfluentRegistryStrategy,
              keySubject: String = "",
              keyRegistry: String = "",
              valueSubject: String = "",
              valueRegistry: String = "") : this(topic = topic,
                                                 keyType = keyType.name,
                                                 key = key,
                                                 valueType = valueType.name,
                                                 value = value,
                                                 properties = properties,
                                                 compression = compression.name,
                                                 acks = acks.name,
                                                 idempotence = idempotence,
                                                 forcePartition = forcePartition,
                                                 keyStrategy = keyStrategy,
                                                 valueStrategy = valueStrategy,
                                                 keySubject = keySubject,
                                                 keyRegistry = keyRegistry,
                                                 valueSubject = valueSubject,
                                                 valueRegistry = valueRegistry)

  fun getKeyType(): FieldType = FieldType.values().find { it.name == keyType } ?: FieldType.STRING
  fun getValueType(): FieldType = FieldType.values().find { it.name == valueType } ?: FieldType.STRING
  fun getCompression(): RecordCompression = RecordCompression.values().find { it.name == compression } ?: RecordCompression.NONE
  fun getAsks(): AcksType = AcksType.values().find { it.name == acks } ?: AcksType.NONE
}