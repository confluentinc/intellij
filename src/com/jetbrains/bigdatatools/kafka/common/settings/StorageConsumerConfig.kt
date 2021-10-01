package com.jetbrains.bigdatatools.kafka.common.settings

import com.jetbrains.bigdatatools.kafka.common.models.FieldType
import com.jetbrains.bigdatatools.kafka.consumer.models.*

data class StorageConsumerConfig(var topic: String,
                                 var keyType: String,
                                 var valueType: String,
                                 var filter: Map<String, String>,
                                 var limit: Map<String, String>,
                                 var partitions: String,
                                 var startWith: Map<String, String>) {
  fun fromStorage(): RunConsumerConfig {
    val limit = ConsumerLimit(
      type = ConsumerLimitType.values().find { it.name == filter["type"] } ?: ConsumerLimitType.NONE,
      value = filter["value"] ?: "",
      time = filter["time"]?.toLongOrNull(),
    )

    val filter = ConsumerFilter(
      type = ConsumerFilterType.values().find { it.name == filter["type"] } ?: ConsumerFilterType.NONE,
      filterKey = filter["key"] ?: "",
      filterValue = filter["value"] ?: "",
      filterHeadKey = filter["headKey"] ?: "",
      filterHeadValue = filter["headValue"] ?: "")
    val startWith = ConsumerStartWith(
      startWith["offset"]?.toLongOrNull(),
      startWith["time"]?.toLongOrNull())
    return RunConsumerConfig(
      topic = topic,
      keyType = FieldType.values().find { it.name == keyType } ?: FieldType.STRING,
      valueType = FieldType.values().find { it.name == valueType } ?: FieldType.STRING,
      filter, limit, partitions, startWith
    )
  }

  companion object {
    fun toStorage(config: RunConsumerConfig) = StorageConsumerConfig(
      config.topic,
      config.keyType.name,
      config.valueType.name,
      mapOf(
        "type" to config.filter.type.name,
        "key" to (config.filter.filterKey ?: ""),
        "value" to (config.filter.filterValue ?: ""),
        "headKey" to (config.filter.filterHeadKey ?: ""),
        "headValue" to (config.filter.filterHeadValue ?: ""),
      ),
      mapOf(
        "type" to config.limit.type.name,
        "value" to config.limit.value,
        "time" to (config.limit.time?.toString() ?: ""),
      ),
      config.partitions,
      mapOf(
        "offset" to (config.startWith.offset?.toString() ?: ""),
        "time" to (config.startWith.time?.toString() ?: ""),
      )
    )
  }
}