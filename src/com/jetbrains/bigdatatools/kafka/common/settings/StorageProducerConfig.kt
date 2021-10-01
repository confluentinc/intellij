package com.jetbrains.bigdatatools.kafka.common.settings

import com.jetbrains.bigdatatools.kafka.common.models.FieldType
import com.jetbrains.bigdatatools.kafka.producer.models.AcksType
import com.jetbrains.bigdatatools.kafka.producer.models.RecordCompression
import com.jetbrains.bigdatatools.kafka.producer.models.RunProducerConfig
import com.jetbrains.bigdatatools.settings.connections.Property

data class StorageProducerConfig(var topic: String,
                                 var keyType: String,
                                 var key: String,
                                 var valueType: String,
                                 var value: String,
                                 var properties: List<Property>,
                                 var compression: String,
                                 var acks: String,
                                 var idempotence: Boolean,
                                 var forcePartition: Int) {
  fun fromStorage() = RunProducerConfig(
    topic = topic,
    keyType = FieldType.values().find { it.name == keyType } ?: FieldType.STRING,
    valueType = FieldType.values().find { it.name == valueType } ?: FieldType.STRING,
    value = value,
    key = key,
    properties = properties,
    idempotence = idempotence,
    forcePartition = forcePartition,
    compression = RecordCompression.values().find { it.name == compression } ?: RecordCompression.NONE,
    acks = AcksType.values().find { it.name == acks } ?: AcksType.NONE
  )

  companion object {
    fun toStorage(config: RunProducerConfig) = StorageProducerConfig(
      topic = config.topic,
      keyType = config.keyType.name,
      valueType = config.valueType.name,
      key = config.key,
      value = config.value,
      acks = config.acks.name,
      compression = config.compression.name,
      forcePartition = config.forcePartition,
      idempotence = config.idempotence,
      properties = config.properties
    )
  }
}