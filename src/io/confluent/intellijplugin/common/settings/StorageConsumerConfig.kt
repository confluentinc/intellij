package io.confluent.intellijplugin.common.settings

import io.confluent.intellijplugin.common.models.KafkaFieldType
import io.confluent.intellijplugin.consumer.models.*
import io.confluent.intellijplugin.registry.KafkaRegistryFormat

data class StorageConsumerConfig(
    var topic: String? = "",
    var keyType: String? = "",
    var valueType: String? = "",
    var connectionType: String? = null,
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

    val keyRegistryType: String = "",
    val valueRegistryType: String = "",

    val keySubject: String = "",
    val keyRegistry: String = "",

    val valueSubject: String = "",
    val valueRegistry: String = "",

    val keyFormat: String = "",
    val valueFormat: String = "",

    val keySchemaId: String = "",
    val valueSchemaId: String = "",

    val consumerGroup: ConsumerGroup? = null,

    var customKeySchema: CustomSchemaData? = null,
    var customValueSchema: CustomSchemaData? = null,
) : StorageConfig {

    constructor(
        topic: String,
        keyType: KafkaFieldType,
        valueType: KafkaFieldType,
        filter: ConsumerFilter,
        limit: ConsumerLimit,
        partitions: String,
        startWith: ConsumerStartWith,
        properties: Map<String, String>,
        settings: Map<String, String>,

        keyFormat: KafkaRegistryFormat,
        valueFormat: KafkaRegistryFormat,
        customKeySchema: CustomSchemaData?,
        customValueSchema: CustomSchemaData?,

        keySubject: String = "",
        valueSubject: String = "",
        consumerGroup: ConsumerGroup? = null,
    ) : this(
        topic = topic,
        keyType = keyType.name,
        valueType = valueType.name,
        filter = createFilterMap(filter),
        limit = createLimit(limit),
        partitions = partitions,
        startWith = createStartWithMap(startWith),
        properties = properties,
        settings = settings,
        keySubject = keySubject,
        valueSubject = valueSubject,

        keyFormat = keyFormat.name,
        valueFormat = valueFormat.name,
        consumerGroup = consumerGroup,

        customKeySchema = customKeySchema,
        customValueSchema = customValueSchema
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

        private fun createLimit(limit: ConsumerLimit) = mapOf(
            "type" to limit.type.name,
            "value" to limit.value,
            "time" to (limit.time?.toString() ?: "")
        )

        private fun createStartWithMap(startWith: ConsumerStartWith): Map<String, String> {
            val startWithMap = mutableMapOf(
                "type" to (startWith.type.toString()),
                "time" to (startWith.time?.toString() ?: ""),
                "offset" to (startWith.offset?.toString() ?: "")
            )

            if (startWith.consumerGroup != null) {
                startWithMap["consumerGroup"] = startWith.consumerGroup
            }
            return startWithMap
        }
    }

    fun getInnerTopic(): String = topic ?: ""

    fun getLimit(): ConsumerLimit {
        val consumerLimitType =
            ConsumerLimitType.entries.firstOrNull { it.name == limit["type"] } ?: ConsumerLimitType.NONE

        return ConsumerLimit(
            type = consumerLimitType,
            value = limit["value"] ?: "",
            time = if (consumerLimitType == ConsumerLimitType.DATE) limit["time"]?.toLongOrNull() else null
        )
    }

    fun getFilter(): ConsumerFilter {
        return ConsumerFilter(
            type = ConsumerFilterType.entries.firstOrNull { it.name == filter["type"] } ?: ConsumerFilterType.NONE,
            filterKey = filter["key"],
            filterValue = filter["value"],
            filterHeadKey = filter["headKey"],
            filterHeadValue = filter["headValue"])
    }

    fun getStartsWith(): ConsumerStartWith {
        return ConsumerStartWith(ConsumerStartType.entries.firstOrNull { it.name == startWith["type"] }
            ?: ConsumerStartType.NOW,
            startWith["time"]?.toLongOrNull(),
            startWith["offset"]?.toLongOrNull(),
            startWith["consumerGroup"])
    }

    fun getKeyType(): KafkaFieldType =
        KafkaFieldType.entries.firstOrNull { it.name == keyType } ?: KafkaFieldType.STRING

    fun getValueType(): KafkaFieldType =
        KafkaFieldType.entries.firstOrNull { it.name == valueType } ?: KafkaFieldType.STRING

    fun getKeyFormat(): KafkaRegistryFormat = KafkaRegistryFormat.parse(keyFormat)
    fun getValueFormat(): KafkaRegistryFormat = KafkaRegistryFormat.parse(valueFormat)
}