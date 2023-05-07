package com.jetbrains.bigdatatools.kafka.consumer.editor

import com.jetbrains.bigdatatools.kafka.common.models.FieldType
import org.apache.kafka.clients.consumer.ConsumerRecord

data class ConsumerOutputRow(val keyType: FieldType, val valueType: FieldType, val record: Result<ConsumerRecord<Any, Any>>)