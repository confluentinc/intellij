package com.jetbrains.bigdatatools.kafka.common.settings

import com.jetbrains.bigdatatools.kafka.common.models.FieldType
import com.jetbrains.bigdatatools.kafka.consumer.models.*

data class StorageConsumerConfig(var topic: String? = "",
                                 var keyType: String? = "",
                                 var valueType: String? = "",
                                 var filter: Map<String, String> = emptyMap(),
                                 var limit: Map<String, String> = emptyMap(),
                                 var partitions: String? = "",
                                 var startWith: Map<String, String> = emptyMap()) : StorageConfig {

  override fun fromStorage(): RunConsumerConfig {

    val consumerLimitType = ConsumerLimitType.values().firstOrNull { it.name == limit["type"] } ?: ConsumerLimitType.NONE

    val limit = ConsumerLimit(
      type = consumerLimitType,
      value = limit["value"] ?: "",
      time = if (consumerLimitType == ConsumerLimitType.DATE) limit["time"]?.toLongOrNull() else null)

    val filter = ConsumerFilter(
      type = ConsumerFilterType.values().firstOrNull { it.name == filter["type"] } ?: ConsumerFilterType.NONE,
      filterKey = filter["key"],
      filterValue = filter["value"],
      filterHeadKey = filter["headKey"],
      filterHeadValue = filter["headValue"])

    val startWith = ConsumerStartWith(ConsumerStartType.values().firstOrNull { it.name == startWith["type"] } ?: ConsumerStartType.NOW,
      startWith["time"]?.toLongOrNull(),
      startWith["offset"]?.toLongOrNull(),
      startWith["consumerGroup"])

    return RunConsumerConfig(
      topic = topic ?: "",
      keyType = FieldType.values().firstOrNull { it.name == keyType } ?: FieldType.STRING,
      valueType = FieldType.values().firstOrNull { it.name == valueType } ?: FieldType.STRING,
      filter, limit, partitions ?: "", startWith
    )
  }
}