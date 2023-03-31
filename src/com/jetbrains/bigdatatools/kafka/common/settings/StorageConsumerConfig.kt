package com.jetbrains.bigdatatools.kafka.common.settings

import com.jetbrains.bigdatatools.kafka.common.models.FieldType
import com.jetbrains.bigdatatools.kafka.consumer.models.*
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryConsumerType

data class StorageConsumerConfig(var topic: String? = "",
                                 var keyType: String? = "",
                                 var valueType: String? = "",
                                 var filter: Map<String, String> = emptyMap(),
                                 var limit: Map<String, String> = emptyMap(),
                                 var partitions: String? = "",
                                 var startWith: Map<String, String> = emptyMap(),
  // Properties from org.apache.kafka.clients.consumer.ConsumerConfig.
  // All properties stored as string even when they are Int. Conversion to proper type done in
  // KafkaConsumerClient.createConsumer().
                                 var properties: Map<String, String> = emptyMap(),
  // Our settings like "Display only last 100 records".
                                 var settings: Map<String, String> = emptyMap(),

                                 val keyRegistryType: String,
                                 val valueRegistryType: String,

                                 val keySubject: String = "",
                                 val keyRegistry: String = "",
                                 val valueSubject: String = "",
                                 val valueRegistry: String = "",

                                 val keyCustomSchema: String = "",
                                 val valueCustomSchema: String = "",

                                 val keySchemaId: String = "",
                                 val valueSchemaId: String = "") : StorageConfig {

  constructor(topic: String,
              keyType: FieldType,
              valueType: FieldType,
              filter: ConsumerFilter,
              limit: ConsumerLimit,
              partitions: String,
              startWith: ConsumerStartWith,
              properties: Map<String, String>,
              settings: Map<String, String>,
              keyRegistryType: String,
              valueRegistryType: String,

              keySubject: String = "",
              valueSubject: String = "",

              keyCustomSchema: String = "",
              valueCustomSchema: String = "",

              keySchemaId: String = "",
              valueSchemaId: String = ""
  ) : this(topic = topic,
           keyType = keyType.name,
           valueType = valueType.name,
           filter = createFilterMap(filter),
           limit = createLimit(limit),
           partitions = partitions,
           startWith = createStartWithMap(startWith),
           properties = properties,
           settings = settings,
           keyRegistryType = keyRegistryType,
           valueRegistryType = valueRegistryType,
           keySubject = keySubject,
           valueSubject = valueSubject,
           keyCustomSchema = keyCustomSchema,
           valueCustomSchema = valueCustomSchema,
           keySchemaId = keySchemaId,
           valueSchemaId = valueSchemaId
  )

  companion object {
    private fun createFilterMap(filter: ConsumerFilter): Map<String, String> {
      val consumerFilterMap = mutableMapOf("type" to filter.type.name)
      filter.filterKey?.let { consumerFilterMap["key"] = it }
      filter.filterValue?.let { consumerFilterMap["value"] = it }
      filter.filterHeadKey?.let { consumerFilterMap["headKey"] = it }
      filter.filterHeadValue?.let { consumerFilterMap["headValue"] = it }
      return consumerFilterMap
    }

    private fun createLimit(limit: ConsumerLimit) = mapOf("type" to limit.type.name,
                                                          "value" to limit.value,
                                                          "time" to (limit.time?.toString() ?: ""))

    private fun createStartWithMap(startWith: ConsumerStartWith): Map<String, String> {
      val startWithMap = mutableMapOf("type" to (startWith.type.toString()),
                                      "time" to (startWith.time?.toString() ?: ""),
                                      "offset" to (startWith.offset?.toString() ?: ""))

      if (startWith.consumerGroup != null) {
        startWithMap["consumerGroup"] = startWith.consumerGroup
      }
      return startWithMap
    }
  }

  fun getInnerTopic(): String = topic ?: ""

  fun getLimit(): ConsumerLimit {
    val consumerLimitType = ConsumerLimitType.values().firstOrNull { it.name == limit["type"] } ?: ConsumerLimitType.NONE

    return ConsumerLimit(
      type = consumerLimitType,
      value = limit["value"] ?: "",
      time = if (consumerLimitType == ConsumerLimitType.DATE) limit["time"]?.toLongOrNull() else null)
  }

  fun getFilter(): ConsumerFilter {
    return ConsumerFilter(
      type = ConsumerFilterType.values().firstOrNull { it.name == filter["type"] } ?: ConsumerFilterType.NONE,
      filterKey = filter["key"],
      filterValue = filter["value"],
      filterHeadKey = filter["headKey"],
      filterHeadValue = filter["headValue"])
  }

  fun getStartsWith(): ConsumerStartWith {
    return ConsumerStartWith(ConsumerStartType.values().firstOrNull { it.name == startWith["type"] } ?: ConsumerStartType.NOW,
                             startWith["time"]?.toLongOrNull(),
                             startWith["offset"]?.toLongOrNull(),
                             startWith["consumerGroup"])
  }

  fun getKeyType(): FieldType = FieldType.values().firstOrNull { it.name == keyType } ?: FieldType.STRING
  fun getValueType(): FieldType = FieldType.values().firstOrNull { it.name == valueType } ?: FieldType.STRING

  fun getKeyRegistryType(): KafkaRegistryConsumerType = KafkaRegistryConsumerType.values().firstOrNull { it.name == keyRegistryType }
                                                        ?: KafkaRegistryConsumerType.AUTO

  fun getValueRegistryType(): KafkaRegistryConsumerType = KafkaRegistryConsumerType.values().firstOrNull { it.name == valueRegistryType }
                                                          ?: KafkaRegistryConsumerType.AUTO
}