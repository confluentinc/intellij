package com.jetbrains.bigdatatools.kafka.consumer.models

import com.jetbrains.bigdatatools.kafka.common.models.FieldType
import com.jetbrains.bigdatatools.kafka.common.models.RunConfig
import com.jetbrains.bigdatatools.kafka.common.settings.StorageConsumerConfig

data class RunConsumerConfig(val topic: String,
                             val keyType: FieldType,
                             val valueType: FieldType,
                             val filter: ConsumerFilter,
                             val limit: ConsumerLimit,
                             val partitions: String,
                             val startWith: ConsumerStartWith) : RunConfig {

  override fun toStorage(): StorageConsumerConfig {

    val startWithMap = mutableMapOf("type" to (startWith.type.toString()),
      "time" to (startWith.time?.toString() ?: ""),
      "offset" to (startWith.offset?.toString() ?: "")
    )

    if (startWith.consumerGroup != null) {
      startWithMap["consumerGroup"] = startWith.consumerGroup
    }

    val consumerFilterMap = mutableMapOf("type" to filter.type.name)
    filter.filterKey?.let { consumerFilterMap["key"] = it }
    filter.filterValue?.let { consumerFilterMap["value"] = it }
    filter.filterHeadKey?.let { consumerFilterMap["headKey"] = it }
    filter.filterHeadValue?.let { consumerFilterMap["headValue"] = it }

    return StorageConsumerConfig(
      this.topic,
      this.keyType.name,
      this.valueType.name,
      consumerFilterMap,
      mapOf(
        "type" to this.limit.type.name,
        "value" to this.limit.value,
        "time" to (this.limit.time?.toString() ?: ""),
      ),
      this.partitions,
      startWithMap
    )
  }
}