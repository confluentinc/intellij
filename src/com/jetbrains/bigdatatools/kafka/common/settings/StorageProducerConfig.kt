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


  fun getKeyType(): FieldType = FieldType.values().find { it.name == keyType } ?: FieldType.STRING
  fun getValueType(): FieldType = FieldType.values().find { it.name == valueType } ?: FieldType.STRING
  fun getCompression(): RecordCompression = RecordCompression.values().find { it.name == compression } ?: RecordCompression.NONE
  fun getAsks(): AcksType = AcksType.values().find { it.name == acks } ?: AcksType.NONE
}