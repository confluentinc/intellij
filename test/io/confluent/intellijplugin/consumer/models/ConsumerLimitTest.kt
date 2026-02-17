package io.confluent.intellijplugin.consumer.models

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@TestApplication
class ConsumerLimitTest {

    @Nested
    @DisplayName("ConsumerLimit computed properties")
    inner class ComputedProperties {

        @Test
        fun `should parse TOPIC_NUMBER_RECORDS from valid numeric string`() {
            val consumerLimit = ConsumerLimit(ConsumerLimitType.TOPIC_NUMBER_RECORDS, "100", null)
            assertEquals(100L, consumerLimit.topicRecordsCount)
            assertNull(consumerLimit.topicRecordsSize)
            assertNull(consumerLimit.partitionRecordsCount)
            assertNull(consumerLimit.partitionRecordsSize)
        }

        @Test
        fun `should return null topicRecordsCount for non-TOPIC_NUMBER_RECORDS types`() {
            val consumerLimit = ConsumerLimit(ConsumerLimitType.TOPIC_MAX_SIZE, "100", null)
            assertNull(consumerLimit.topicRecordsCount)
        }

        @Test
        fun `should parse TOPIC_MAX_SIZE from valid numeric string`() {
            val consumerLimit = ConsumerLimit(ConsumerLimitType.TOPIC_MAX_SIZE, "500000", null)
            assertNull(consumerLimit.topicRecordsCount)
            assertEquals(500000L, consumerLimit.topicRecordsSize)
            assertNull(consumerLimit.partitionRecordsCount)
            assertNull(consumerLimit.partitionRecordsSize)
        }

        @Test
        fun `should return null topicRecordsSize for non-TOPIC_MAX_SIZE types`() {
            val consumerLimit = ConsumerLimit(ConsumerLimitType.TOPIC_NUMBER_RECORDS, "100", null)
            assertNull(consumerLimit.topicRecordsSize)
        }

        @Test
        fun `should parse PARTITION_NUMBER_RECORDS from valid numeric string`() {
            val consumerLimit = ConsumerLimit(ConsumerLimitType.PARTITION_NUMBER_RECORDS, "250", null)
            assertNull(consumerLimit.topicRecordsCount)
            assertNull(consumerLimit.topicRecordsSize)
            assertEquals(250L, consumerLimit.partitionRecordsCount)
            assertNull(consumerLimit.partitionRecordsSize)
        }

        @Test
        fun `should return null partitionRecordsCount for non-PARTITION_NUMBER_RECORDS types`() {
            val consumerLimit = ConsumerLimit(ConsumerLimitType.PARTITION_MAX_SIZE, "250", null)
            assertNull(consumerLimit.partitionRecordsCount)
        }

        @Test
        fun `should parse PARTITION_MAX_SIZE from valid numeric string`() {
            val consumerLimit = ConsumerLimit(ConsumerLimitType.PARTITION_MAX_SIZE, "1000000", null)
            assertNull(consumerLimit.topicRecordsCount)
            assertNull(consumerLimit.topicRecordsSize)
            assertNull(consumerLimit.partitionRecordsCount)
            assertEquals(1000000L, consumerLimit.partitionRecordsSize)
        }

        @Test
        fun `should return null for invalid numeric strings`() {
            val consumerLimit = ConsumerLimit(ConsumerLimitType.TOPIC_NUMBER_RECORDS, "abc", null)
            assertNull(consumerLimit.topicRecordsCount)
        }

        @Test
        fun `should return null for empty string value`() {
            val consumerLimit = ConsumerLimit(ConsumerLimitType.TOPIC_NUMBER_RECORDS, "", null)
            assertNull(consumerLimit.topicRecordsCount)
        }

        @Test
        fun `should preserve time field for DATE limit type`() {
            val consumerLimit = ConsumerLimit(ConsumerLimitType.DATE, "", 1700000000000L)
            assertEquals(1700000000000L, consumerLimit.time)
        }

        @Test
        fun `should have all computed properties null for NONE type`() {
            val consumerLimit = ConsumerLimit(ConsumerLimitType.NONE, "", null)
            assertNull(consumerLimit.topicRecordsCount)
            assertNull(consumerLimit.topicRecordsSize)
            assertNull(consumerLimit.partitionRecordsCount)
            assertNull(consumerLimit.partitionRecordsSize)
        }
    }
}
