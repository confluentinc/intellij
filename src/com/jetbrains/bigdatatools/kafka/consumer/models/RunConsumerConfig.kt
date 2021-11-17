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
    return StorageConsumerConfig(
      this.topic,
      this.keyType.name,
      this.valueType.name,
      mapOf(
        "type" to this.filter.type.name,
        "key" to (this.filter.filterKey ?: ""),
        "value" to (this.filter.filterValue ?: ""),
        "headKey" to (this.filter.filterHeadKey ?: ""),
        "headValue" to (this.filter.filterHeadValue ?: ""),
      ),
      mapOf(
        "type" to this.limit.type.name,
        "value" to this.limit.value,
        "time" to (this.limit.time?.toString() ?: ""),
      ),
      this.partitions,
      mapOf(
        "type" to (this.startWith.type.toString()),
        "time" to (this.startWith.time?.toString() ?: ""),
        "offset" to (this.startWith.offset?.toString() ?: ""),
        "consumerGroup" to (this.startWith.consumerGroup ?: "")
      )
    )
  }
}