package io.confluent.intellijplugin.consumer.editor

import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.common.models.KafkaFieldType
import io.confluent.intellijplugin.registry.KafkaRegistryFormat
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.*

@TestApplication
class KafkaRecordTest {

    private fun consumerRecord(
        key: Any? = "test-key",
        value: Any? = "test-value",
        topic: String = "test-topic",
        partition: Int = 0,
        offset: Long = 42L,
        timestamp: Long = 1700000000000L,
        keySize: Int = 8,
        valueSize: Int = 10,
        headers: RecordHeaders = RecordHeaders()
    ): ConsumerRecord<Any, Any> = ConsumerRecord(
        topic, partition, offset, timestamp,
        TimestampType.CREATE_TIME, keySize, valueSize,
        key, value, headers, Optional.empty()
    )

    @Nested
    @DisplayName("createFor - successful ConsumerRecord")
    inner class CreateForSuccess {

        @Test
        fun `should map topic, partition, offset, and timestamp from ConsumerRecord`() {
            val rec = consumerRecord(
                topic = "my-topic", partition = 3, offset = 100L, timestamp = 1700000000000L
            )
            val kafkaRecord = KafkaRecord.createFor(
                KafkaFieldType.STRING, KafkaFieldType.STRING,
                KafkaRegistryFormat.UNKNOWN, KafkaRegistryFormat.UNKNOWN,
                Result.success(rec)
            )
            assertEquals("my-topic", kafkaRecord.topic)
            assertEquals(3, kafkaRecord.partition)
            assertEquals(100L, kafkaRecord.offset)
            assertEquals(1700000000000L, kafkaRecord.timestamp)
        }

        @Test
        fun `should map key and value from ConsumerRecord`() {
            val rec = consumerRecord(key = "k1", value = "v1")
            val kafkaRecord = KafkaRecord.createFor(
                KafkaFieldType.STRING, KafkaFieldType.STRING,
                KafkaRegistryFormat.UNKNOWN, KafkaRegistryFormat.UNKNOWN,
                Result.success(rec)
            )
            assertEquals("k1", kafkaRecord.key)
            assertEquals("v1", kafkaRecord.value)
        }

        @Test
        fun `should extract headers as Property list`() {
            val headers = RecordHeaders(listOf(
                RecordHeader("h1", "val1".toByteArray()),
                RecordHeader("h2", "val2".toByteArray())
            ))
            val rec = consumerRecord(headers = headers)
            val kafkaRecord = KafkaRecord.createFor(
                KafkaFieldType.STRING, KafkaFieldType.STRING,
                KafkaRegistryFormat.UNKNOWN, KafkaRegistryFormat.UNKNOWN,
                Result.success(rec)
            )
            assertEquals(2, kafkaRecord.headers.size)
            assertEquals("h1", kafkaRecord.headers[0].name)
            assertEquals("val1", kafkaRecord.headers[0].value)
            assertEquals("h2", kafkaRecord.headers[1].name)
            assertEquals("val2", kafkaRecord.headers[1].value)
        }

        @Test
        fun `should set error to null for successful records`() {
            val rec = consumerRecord()
            val kafkaRecord = KafkaRecord.createFor(
                KafkaFieldType.STRING, KafkaFieldType.STRING,
                KafkaRegistryFormat.UNKNOWN, KafkaRegistryFormat.UNKNOWN,
                Result.success(rec)
            )
            assertNull(kafkaRecord.error)
        }

        @Test
        fun `should default keyType to STRING when passed null`() {
            val rec = consumerRecord()
            val kafkaRecord = KafkaRecord.createFor(
                null, null,
                null, null,
                Result.success(rec)
            )
            assertEquals(KafkaFieldType.STRING, kafkaRecord.keyType)
            assertEquals(KafkaFieldType.STRING, kafkaRecord.valueType)
        }

        @Test
        fun `should default keyFormat and valueFormat to UNKNOWN when passed null`() {
            val rec = consumerRecord()
            val kafkaRecord = KafkaRecord.createFor(
                KafkaFieldType.STRING, KafkaFieldType.STRING,
                null, null,
                Result.success(rec)
            )
            assertEquals(KafkaRegistryFormat.UNKNOWN, kafkaRecord.keyFormat)
            assertEquals(KafkaRegistryFormat.UNKNOWN, kafkaRecord.valueFormat)
        }

        @Test
        fun `should map serialized key and value sizes`() {
            val rec = consumerRecord(keySize = 15, valueSize = 200)
            val kafkaRecord = KafkaRecord.createFor(
                KafkaFieldType.STRING, KafkaFieldType.STRING,
                KafkaRegistryFormat.UNKNOWN, KafkaRegistryFormat.UNKNOWN,
                Result.success(rec)
            )
            assertEquals(15, kafkaRecord.keySize)
            assertEquals(200, kafkaRecord.valueSize)
        }

        @Test
        fun `should handle empty headers`() {
            val rec = consumerRecord(headers = RecordHeaders())
            val kafkaRecord = KafkaRecord.createFor(
                KafkaFieldType.STRING, KafkaFieldType.STRING,
                KafkaRegistryFormat.UNKNOWN, KafkaRegistryFormat.UNKNOWN,
                Result.success(rec)
            )
            assertTrue(kafkaRecord.headers.isEmpty())
        }
    }

    @Nested
    @DisplayName("createFor - failed Result")
    inner class CreateForFailure {

        @Test
        fun `should create error record from failed Result with exception details`() {
            val exception = RuntimeException("Deserialization failed")
            val kafkaRecord = KafkaRecord.createFor(
                KafkaFieldType.STRING, KafkaFieldType.STRING,
                KafkaRegistryFormat.UNKNOWN, KafkaRegistryFormat.UNKNOWN,
                Result.failure(exception)
            )
            assertEquals(exception, kafkaRecord.error)
        }

        @Test
        fun `should set key and value to null for failed records`() {
            val kafkaRecord = KafkaRecord.createFor(
                KafkaFieldType.STRING, KafkaFieldType.STRING,
                KafkaRegistryFormat.UNKNOWN, KafkaRegistryFormat.UNKNOWN,
                Result.failure(RuntimeException("fail"))
            )
            assertNull(kafkaRecord.key)
            assertNull(kafkaRecord.value)
        }

        @Test
        fun `should use errorPartition and errorOffset for failed records`() {
            val kafkaRecord = KafkaRecord.createFor(
                KafkaFieldType.STRING, KafkaFieldType.STRING,
                KafkaRegistryFormat.UNKNOWN, KafkaRegistryFormat.UNKNOWN,
                Result.failure(RuntimeException("fail")),
                errorPartition = 5,
                errorOffset = 99L
            )
            assertEquals(5, kafkaRecord.partition)
            assertEquals(99L, kafkaRecord.offset)
        }

        @Test
        fun `should default partition to -1 and offset to -1 when not provided`() {
            val kafkaRecord = KafkaRecord.createFor(
                KafkaFieldType.STRING, KafkaFieldType.STRING,
                KafkaRegistryFormat.UNKNOWN, KafkaRegistryFormat.UNKNOWN,
                Result.failure(RuntimeException("fail"))
            )
            assertEquals(-1, kafkaRecord.partition)
            assertEquals(-1L, kafkaRecord.offset)
        }

        @Test
        fun `should set empty topic and empty headers for failed records`() {
            val kafkaRecord = KafkaRecord.createFor(
                KafkaFieldType.STRING, KafkaFieldType.STRING,
                KafkaRegistryFormat.UNKNOWN, KafkaRegistryFormat.UNKNOWN,
                Result.failure(RuntimeException("fail"))
            )
            assertEquals("", kafkaRecord.topic)
            assertTrue(kafkaRecord.headers.isEmpty())
        }

        @Test
        fun `should set keyFormat and valueFormat independently for failed records`() {
            val kafkaRecord = KafkaRecord.createFor(
                KafkaFieldType.STRING, KafkaFieldType.STRING,
                KafkaRegistryFormat.AVRO, KafkaRegistryFormat.PROTOBUF,
                Result.failure(RuntimeException("fail"))
            )
            assertEquals(KafkaRegistryFormat.AVRO, kafkaRecord.keyFormat)
            assertEquals(KafkaRegistryFormat.PROTOBUF, kafkaRecord.valueFormat)
        }
    }

    @Nested
    @DisplayName("Computed properties")
    inner class ComputedProperties {

        @Test
        fun `should compute keyText using KafkaEditorUtils when no error`() {
            val rec = consumerRecord(key = "my-key")
            val kafkaRecord = KafkaRecord.createFor(
                KafkaFieldType.STRING, KafkaFieldType.STRING,
                KafkaRegistryFormat.UNKNOWN, KafkaRegistryFormat.UNKNOWN,
                Result.success(rec)
            )
            // For STRING type with UNKNOWN format, getValueAsString returns toString()
            assertEquals("my-key", kafkaRecord.keyText)
        }

        @Test
        fun `should compute valueText using KafkaEditorUtils when no error`() {
            val rec = consumerRecord(value = "my-value")
            val kafkaRecord = KafkaRecord.createFor(
                KafkaFieldType.STRING, KafkaFieldType.STRING,
                KafkaRegistryFormat.UNKNOWN, KafkaRegistryFormat.UNKNOWN,
                Result.success(rec)
            )
            assertEquals("my-value", kafkaRecord.valueText)
        }

        @Test
        fun `should return null for keyText when error is present`() {
            val kafkaRecord = KafkaRecord.createFor(
                KafkaFieldType.STRING, KafkaFieldType.STRING,
                KafkaRegistryFormat.UNKNOWN, KafkaRegistryFormat.UNKNOWN,
                Result.failure(RuntimeException("fail"))
            )
            assertNull(kafkaRecord.keyText)
        }

        @Test
        fun `should return null for valueText when error is present`() {
            val kafkaRecord = KafkaRecord.createFor(
                KafkaFieldType.STRING, KafkaFieldType.STRING,
                KafkaRegistryFormat.UNKNOWN, KafkaRegistryFormat.UNKNOWN,
                Result.failure(RuntimeException("fail"))
            )
            assertNull(kafkaRecord.valueText)
        }

        @Test
        fun `should compute errorText from exception message`() {
            val kafkaRecord = KafkaRecord.createFor(
                KafkaFieldType.STRING, KafkaFieldType.STRING,
                KafkaRegistryFormat.UNKNOWN, KafkaRegistryFormat.UNKNOWN,
                Result.failure(RuntimeException("Something went wrong"))
            )
            assertEquals("Something went wrong", kafkaRecord.errorText)
        }

        @Test
        fun `should compute errorText as class simple name when message is null`() {
            val kafkaRecord = KafkaRecord.createFor(
                KafkaFieldType.STRING, KafkaFieldType.STRING,
                KafkaRegistryFormat.UNKNOWN, KafkaRegistryFormat.UNKNOWN,
                Result.failure(RuntimeException())
            )
            assertEquals("RuntimeException", kafkaRecord.errorText)
        }

        @Test
        fun `should return empty string for keyText when key is null and no error`() {
            val rec = consumerRecord(key = null)
            val kafkaRecord = KafkaRecord.createFor(
                KafkaFieldType.STRING, KafkaFieldType.STRING,
                KafkaRegistryFormat.UNKNOWN, KafkaRegistryFormat.UNKNOWN,
                Result.success(rec)
            )
            // KafkaEditorUtils.getValueAsString returns "" when value is null
            assertEquals("", kafkaRecord.keyText)
        }
    }

    @Nested
    @DisplayName("Lazy text rendering with truncation")
    inner class LazyTextRendering {

        @Test
        fun `should truncate keyTextTruncated to MAX_CELL_LENGTH with ellipsis`() {
            val longKey = "x".repeat(500)
            val rec = consumerRecord(key = longKey)
            val kafkaRecord = KafkaRecord.createFor(
                KafkaFieldType.STRING, KafkaFieldType.STRING,
                KafkaRegistryFormat.UNKNOWN, KafkaRegistryFormat.UNKNOWN,
                Result.success(rec)
            )
            assertEquals(KafkaRecord.MAX_CELL_LENGTH + 1, kafkaRecord.keyTextTruncated.length)
            assertTrue(kafkaRecord.keyTextTruncated.startsWith("x".repeat(KafkaRecord.MAX_CELL_LENGTH)))
            assertTrue(kafkaRecord.keyTextTruncated.endsWith("\u2026"))
        }

        @Test
        fun `should truncate valueTextTruncated to MAX_CELL_LENGTH with ellipsis`() {
            val longValue = "y".repeat(500)
            val rec = consumerRecord(value = longValue)
            val kafkaRecord = KafkaRecord.createFor(
                KafkaFieldType.STRING, KafkaFieldType.STRING,
                KafkaRegistryFormat.UNKNOWN, KafkaRegistryFormat.UNKNOWN,
                Result.success(rec)
            )
            assertEquals(KafkaRecord.MAX_CELL_LENGTH + 1, kafkaRecord.valueTextTruncated.length)
            assertTrue(kafkaRecord.valueTextTruncated.startsWith("y".repeat(KafkaRecord.MAX_CELL_LENGTH)))
            assertTrue(kafkaRecord.valueTextTruncated.endsWith("\u2026"))
        }

        @Test
        fun `should not pad short text`() {
            val rec = consumerRecord(key = "short", value = "tiny")
            val kafkaRecord = KafkaRecord.createFor(
                KafkaFieldType.STRING, KafkaFieldType.STRING,
                KafkaRegistryFormat.UNKNOWN, KafkaRegistryFormat.UNKNOWN,
                Result.success(rec)
            )
            assertEquals("short", kafkaRecord.keyTextTruncated)
            assertEquals("tiny", kafkaRecord.valueTextTruncated)
        }

        @Test
        fun `should return empty string for truncated text when error present`() {
            val kafkaRecord = KafkaRecord.createFor(
                KafkaFieldType.STRING, KafkaFieldType.STRING,
                KafkaRegistryFormat.UNKNOWN, KafkaRegistryFormat.UNKNOWN,
                Result.failure(RuntimeException("fail"))
            )
            assertEquals("", kafkaRecord.keyTextTruncated)
            assertEquals("", kafkaRecord.valueTextTruncated)
        }

        @Test
        fun `should not truncate string of exactly MAX_CELL_LENGTH`() {
            val exactLengthKey = "a".repeat(KafkaRecord.MAX_CELL_LENGTH)
            val exactLengthValue = "b".repeat(KafkaRecord.MAX_CELL_LENGTH)
            val rec = consumerRecord(key = exactLengthKey, value = exactLengthValue)
            val kafkaRecord = KafkaRecord.createFor(
                KafkaFieldType.STRING, KafkaFieldType.STRING,
                KafkaRegistryFormat.UNKNOWN, KafkaRegistryFormat.UNKNOWN,
                Result.success(rec)
            )
            assertEquals(exactLengthKey, kafkaRecord.keyTextTruncated)
            assertEquals(exactLengthValue, kafkaRecord.valueTextTruncated)
            assertEquals(KafkaRecord.MAX_CELL_LENGTH, kafkaRecord.keyTextTruncated.length)
            assertEquals(KafkaRecord.MAX_CELL_LENGTH, kafkaRecord.valueTextTruncated.length)
        }

        @Test
        fun `should preserve full text in keyText and valueText`() {
            val longKey = "k".repeat(500)
            val longValue = "v".repeat(500)
            val rec = consumerRecord(key = longKey, value = longValue)
            val kafkaRecord = KafkaRecord.createFor(
                KafkaFieldType.STRING, KafkaFieldType.STRING,
                KafkaRegistryFormat.UNKNOWN, KafkaRegistryFormat.UNKNOWN,
                Result.success(rec)
            )
            assertEquals(500, kafkaRecord.keyText?.length)
            assertEquals(500, kafkaRecord.valueText?.length)
        }
    }
}
