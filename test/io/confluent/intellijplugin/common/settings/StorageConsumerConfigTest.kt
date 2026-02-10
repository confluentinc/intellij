package io.confluent.intellijplugin.common.settings

import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.common.models.KafkaFieldType
import io.confluent.intellijplugin.consumer.models.*
import io.confluent.intellijplugin.registry.KafkaRegistryFormat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@TestApplication
class StorageConsumerConfigTest {

    private fun createConfig(
        filter: ConsumerFilter = ConsumerFilter(null, null, null, null, ConsumerFilterType.NONE),
        limit: ConsumerLimit = ConsumerLimit(ConsumerLimitType.NONE, "", null),
        startWith: ConsumerStartWith = ConsumerStartWith(ConsumerStartType.NOW, null, null, null),
        keyType: KafkaFieldType = KafkaFieldType.STRING,
        valueType: KafkaFieldType = KafkaFieldType.STRING
    ): StorageConsumerConfig {
        return StorageConsumerConfig(
            topic = "test-topic",
            keyType = keyType,
            valueType = valueType,
            filter = filter,
            limit = limit,
            partitions = "",
            startWith = startWith,
            properties = emptyMap(),
            settings = emptyMap(),
            keyFormat = KafkaRegistryFormat.JSON,
            valueFormat = KafkaRegistryFormat.JSON,
            customKeySchema = null,
            customValueSchema = null
        )
    }

    @Nested
    @DisplayName("Filter round-trip")
    inner class FilterRoundTrip {

        @Test
        fun `should round-trip CONTAINS filter with all fields`() {
            val filter = ConsumerFilter("testKey", "testValue", "headKey", "headValue", ConsumerFilterType.CONTAINS)
            val result = createConfig(filter = filter).getFilter()
            assertEquals(ConsumerFilterType.CONTAINS, result.type)
            assertEquals("testKey", result.filterKey)
            assertEquals("testValue", result.filterValue)
            assertEquals("headKey", result.filterHeadKey)
            assertEquals("headValue", result.filterHeadValue)
        }

        @Test
        fun `should round-trip REGEX filter with null header fields`() {
            val filter = ConsumerFilter("key.*", "val[0-9]+", null, null, ConsumerFilterType.REGEX)
            val result = createConfig(filter = filter).getFilter()
            assertEquals(ConsumerFilterType.REGEX, result.type)
            assertEquals("key.*", result.filterKey)
            assertEquals("val[0-9]+", result.filterValue)
            assertNull(result.filterHeadKey)
            assertNull(result.filterHeadValue)
        }

        @Test
        fun `should round-trip NONE filter`() {
            val filter = ConsumerFilter(null, null, null, null, ConsumerFilterType.NONE)
            val result = createConfig(filter = filter).getFilter()
            assertEquals(ConsumerFilterType.NONE, result.type)
            assertNull(result.filterKey)
            assertNull(result.filterValue)
        }
    }

    @Nested
    @DisplayName("Limit round-trip")
    inner class LimitRoundTrip {

        @Test
        fun `should round-trip TOPIC_NUMBER_RECORDS limit`() {
            val limit = ConsumerLimit(ConsumerLimitType.TOPIC_NUMBER_RECORDS, "1000", null)
            val result = createConfig(limit = limit).getLimit()
            assertEquals(ConsumerLimitType.TOPIC_NUMBER_RECORDS, result.type)
            assertEquals("1000", result.value)
        }

        @Test
        fun `should round-trip DATE limit preserving time`() {
            val limit = ConsumerLimit(ConsumerLimitType.DATE, "100", 1700000000000L)
            val result = createConfig(limit = limit).getLimit()
            assertEquals(ConsumerLimitType.DATE, result.type)
            assertEquals("100", result.value)
            assertEquals(1700000000000L, result.time)
        }
    }

    @Nested
    @DisplayName("StartWith round-trip")
    inner class StartWithRoundTrip {

        @Test
        fun `should round-trip OFFSET start`() {
            val startWith = ConsumerStartWith(ConsumerStartType.OFFSET, null, 12345L, null)
            val result = createConfig(startWith = startWith).getStartsWith()
            assertEquals(ConsumerStartType.OFFSET, result.type)
            assertNull(result.time)
            assertEquals(12345L, result.offset)
            assertNull(result.consumerGroup)
        }

        @Test
        fun `should round-trip CONSUMER_GROUP start`() {
            val startWith = ConsumerStartWith(ConsumerStartType.CONSUMER_GROUP, null, null, "my-group")
            val result = createConfig(startWith = startWith).getStartsWith()
            assertEquals(ConsumerStartType.CONSUMER_GROUP, result.type)
            assertNull(result.time)
            assertNull(result.offset)
            assertEquals("my-group", result.consumerGroup)
        }
    }

    @Nested
    @DisplayName("Field type accessors")
    inner class FieldTypeAccessors {

        @Test
        fun `should round-trip key and value types`() {
            val config = createConfig(keyType = KafkaFieldType.LONG, valueType = KafkaFieldType.JSON)
            assertEquals(KafkaFieldType.LONG, config.getKeyType())
            assertEquals(KafkaFieldType.JSON, config.getValueType())
        }
    }

    @Nested
    @DisplayName("Deserialization fallbacks")
    inner class DeserializationFallbacks {

        @Test
        fun `should default to NONE for unknown filter type`() {
            val config = StorageConsumerConfig(filter = mapOf("type" to "INVALID_TYPE", "key" to "val"))
            assertEquals(ConsumerFilterType.NONE, config.getFilter().type)
        }

        @Test
        fun `should default to NOW for unknown start type`() {
            val config = StorageConsumerConfig(startWith = mapOf("type" to "INVALID_TYPE"))
            assertEquals(ConsumerStartType.NOW, config.getStartsWith().type)
        }

        @Test
        fun `should default to STRING for unknown key type`() {
            val config = StorageConsumerConfig(keyType = "INVALID_TYPE")
            assertEquals(KafkaFieldType.STRING, config.getKeyType())
        }
    }
}
