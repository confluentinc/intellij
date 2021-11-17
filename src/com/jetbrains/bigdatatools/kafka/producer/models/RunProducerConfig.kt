package com.jetbrains.bigdatatools.kafka.producer.models

import com.jetbrains.bigdatatools.kafka.common.models.FieldType
import com.jetbrains.bigdatatools.kafka.common.models.RunConfig
import com.jetbrains.bigdatatools.kafka.common.settings.StorageProducerConfig
import com.jetbrains.bigdatatools.settings.connections.Property

data class RunProducerConfig(val topic: String,
                             val keyType: FieldType,
                             val key: String,
                             val valueType: FieldType,
                             val value: String,
                             val properties: List<Property>,
                             val compression: RecordCompression,
                             val acks: AcksType,
                             val idempotence: Boolean,
                             val forcePartition: Int) : RunConfig {

  override fun toStorage() = StorageProducerConfig(
    topic = this.topic,
    keyType = this.keyType.name,
    valueType = this.valueType.name,
    key = this.key,
    value = this.value,
    acks = this.acks.name,
    compression = this.compression.name,
    forcePartition = this.forcePartition,
    idempotence = this.idempotence,
    properties = this.properties
  )
}