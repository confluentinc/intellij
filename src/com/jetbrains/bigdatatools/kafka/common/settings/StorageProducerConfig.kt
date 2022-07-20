package com.jetbrains.bigdatatools.kafka.common.settings

import com.jetbrains.bigdatatools.kafka.common.models.FieldType
import com.jetbrains.bigdatatools.kafka.producer.models.AcksType
import com.jetbrains.bigdatatools.kafka.producer.models.RecordCompression

import com.jetbrains.bigdatatools.settings.connections.Property

data class StorageProducerConfig(var topic: String = "",
                                 var keyType: String = "",
                                 var key: String = "",
                                 var valueType: String = "",
                                 var value: String = "",
                                 var properties: List<Property> = emptyList(),
                                 var compression: String = "",
                                 var acks: String = "",
                                 var idempotence: Boolean = false,
                                 var forcePartition: Int = -1) : StorageConfig {

  constructor(topic: String,
              keyType: FieldType,
              key: String,
              valueType: FieldType,
              value: String,
              properties: List<Property>,
              compression: RecordCompression,
              acks: AcksType,
              idempotence: Boolean,
              forcePartition: Int) : this(topic = topic,
                                          keyType = keyType.name,
                                          valueType = valueType.name,
                                          key = key,
                                          value = value,
                                          acks = acks.name,
                                          compression = compression.name,
                                          forcePartition = forcePartition,
                                          idempotence = idempotence,
                                          properties = properties)

  fun getKeyType(): FieldType = FieldType.values().find { it.name == keyType } ?: FieldType.STRING
  fun getValueType(): FieldType = FieldType.values().find { it.name == valueType } ?: FieldType.STRING
  fun getCompression(): RecordCompression = RecordCompression.values().find { it.name == compression } ?: RecordCompression.NONE
  fun getAsks(): AcksType = AcksType.values().find { it.name == acks } ?: AcksType.NONE
}