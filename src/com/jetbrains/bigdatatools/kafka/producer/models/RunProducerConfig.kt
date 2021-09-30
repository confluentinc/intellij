package com.jetbrains.bigdatatools.kafka.producer.models

import com.jetbrains.bigdatatools.kafka.common.models.FieldType
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
                             val forcePartition: Int)