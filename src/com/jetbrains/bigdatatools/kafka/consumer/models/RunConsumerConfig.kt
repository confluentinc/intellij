package com.jetbrains.bigdatatools.kafka.consumer.models

import com.jetbrains.bigdatatools.kafka.common.models.FieldType

data class RunConsumerConfig(val topic: String,
                             val keyType: FieldType,
                             val valueType: FieldType,
                             val filter: ConsumerFilter,
                             val limit: ConsumerLimit,
                             val partitions: List<Int>?,
                             val startWith: ConsumerStartWith)