package io.confluent.intellijplugin.consumer.models

import com.intellij.testFramework.junit5.TestApplication
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@TestApplication
class ConsumerFilterTest {

    private fun record(
        key: Any? = "test-key",
        value: Any? = "test-value",
        headers: RecordHeaders = RecordHeaders()
    ): ConsumerRecord<Any, Any> {
        return ConsumerRecord(
            "test-topic",
            0,
            0L,
            0L,
            TimestampType.CREATE_TIME,
            0,
            0,
            key,
            value,
            headers,
            java.util.Optional.empty()
        )
    }

    @Nested
    @DisplayName("NONE filter type")
    inner class NoneFilterType {

        @Test
        fun `should pass all records when filter type is NONE`() {
            val filter = ConsumerFilter(
                filterKey = "something",
                filterValue = "something",
                filterHeadKey = "something",
                filterHeadValue = "something",
                type = ConsumerFilterType.NONE
            )
            val testRecord = record(key = "any-key", value = "any-value")
            assertTrue(filter.isRecordPassFilter(testRecord))
        }

    }

    @Nested
    @DisplayName("Key filtering")
    inner class KeyFiltering {

        @Test
        fun `should pass when key contains filter value`() {
            val filter = ConsumerFilter(
                filterKey = "test",
                filterValue = null,
                filterHeadKey = null,
                filterHeadValue = null,
                type = ConsumerFilterType.CONTAINS
            )
            val testRecord = record(key = "test-key-123")
            assertTrue(filter.isRecordPassFilter(testRecord))
        }

        @Test
        fun `should not pass when key does not contain filter value`() {
            val filter = ConsumerFilter(
                filterKey = "missing",
                filterValue = null,
                filterHeadKey = null,
                filterHeadValue = null,
                type = ConsumerFilterType.CONTAINS
            )
            val testRecord = record(key = "test-key-123")
            assertFalse(filter.isRecordPassFilter(testRecord))
        }

        @Test
        fun `should pass when key does not contain filter value with DOES_NOT_CONTAINS`() {
            val filter = ConsumerFilter(
                filterKey = "missing",
                filterValue = null,
                filterHeadKey = null,
                filterHeadValue = null,
                type = ConsumerFilterType.DOES_NOT_CONTAINS
            )
            val testRecord = record(key = "test-key-123")
            assertTrue(filter.isRecordPassFilter(testRecord))
        }

        @Test
        fun `should not pass when key contains filter value with DOES_NOT_CONTAINS`() {
            val filter = ConsumerFilter(
                filterKey = "test",
                filterValue = null,
                filterHeadKey = null,
                filterHeadValue = null,
                type = ConsumerFilterType.DOES_NOT_CONTAINS
            )
            val testRecord = record(key = "test-key-123")
            assertFalse(filter.isRecordPassFilter(testRecord))
        }

        @Test
        fun `should pass when key matches regex pattern`() {
            val filter = ConsumerFilter(
                filterKey = "test-.*-\\d+",
                filterValue = null,
                filterHeadKey = null,
                filterHeadValue = null,
                type = ConsumerFilterType.REGEX
            )
            val testRecord = record(key = "test-key-123")
            assertTrue(filter.isRecordPassFilter(testRecord))
        }

        @Test
        fun `should not pass when key does not match regex pattern`() {
            val filter = ConsumerFilter(
                filterKey = "^\\d+$",
                filterValue = null,
                filterHeadKey = null,
                filterHeadValue = null,
                type = ConsumerFilterType.REGEX
            )
            val testRecord = record(key = "test-key-123")
            assertFalse(filter.isRecordPassFilter(testRecord))
        }

        @Test
        fun `should pass when filter key is null`() {
            val filter = ConsumerFilter(
                filterKey = null,
                filterValue = null,
                filterHeadKey = null,
                filterHeadValue = null,
                type = ConsumerFilterType.CONTAINS
            )
            val testRecord = record(key = "any-key")
            assertTrue(filter.isRecordPassFilter(testRecord))
        }

        @Test
        fun `should not pass when record key is null and filter key is set with CONTAINS`() {
            val filter = ConsumerFilter(
                filterKey = "test",
                filterValue = null,
                filterHeadKey = null,
                filterHeadValue = null,
                type = ConsumerFilterType.CONTAINS
            )
            val testRecord = record(key = null)
            assertFalse(filter.isRecordPassFilter(testRecord))
        }
    }

    @Nested
    @DisplayName("Value filtering")
    inner class ValueFiltering {

        @Test
        fun `should pass when value contains filter value`() {
            val filter = ConsumerFilter(
                filterKey = null,
                filterValue = "data",
                filterHeadKey = null,
                filterHeadValue = null,
                type = ConsumerFilterType.CONTAINS
            )
            val testRecord = record(value = "some-data-here")
            assertTrue(filter.isRecordPassFilter(testRecord))
        }

        @Test
        fun `should not pass when value does not contain filter value`() {
            val filter = ConsumerFilter(
                filterKey = null,
                filterValue = "missing",
                filterHeadKey = null,
                filterHeadValue = null,
                type = ConsumerFilterType.CONTAINS
            )
            val testRecord = record(value = "some-data-here")
            assertFalse(filter.isRecordPassFilter(testRecord))
        }

        @Test
        fun `should pass when value does not contain filter value with DOES_NOT_CONTAINS`() {
            val filter = ConsumerFilter(
                filterKey = null,
                filterValue = "missing",
                filterHeadKey = null,
                filterHeadValue = null,
                type = ConsumerFilterType.DOES_NOT_CONTAINS
            )
            val testRecord = record(value = "some-data-here")
            assertTrue(filter.isRecordPassFilter(testRecord))
        }

        @Test
        fun `should not pass when value contains filter value with DOES_NOT_CONTAINS`() {
            val filter = ConsumerFilter(
                filterKey = null,
                filterValue = "data",
                filterHeadKey = null,
                filterHeadValue = null,
                type = ConsumerFilterType.DOES_NOT_CONTAINS
            )
            val testRecord = record(value = "some-data-here")
            assertFalse(filter.isRecordPassFilter(testRecord))
        }

        @Test
        fun `should pass when value matches regex pattern`() {
            val filter = ConsumerFilter(
                filterKey = null,
                filterValue = "some-.*-here",
                filterHeadKey = null,
                filterHeadValue = null,
                type = ConsumerFilterType.REGEX
            )
            val testRecord = record(value = "some-data-here")
            assertTrue(filter.isRecordPassFilter(testRecord))
        }

        @Test
        fun `should not pass when value does not match regex pattern`() {
            val filter = ConsumerFilter(
                filterKey = null,
                filterValue = "^\\d+$",
                filterHeadKey = null,
                filterHeadValue = null,
                type = ConsumerFilterType.REGEX
            )
            val testRecord = record(value = "some-data-here")
            assertFalse(filter.isRecordPassFilter(testRecord))
        }

        @Test
        fun `should pass when filter value is null`() {
            val filter = ConsumerFilter(
                filterKey = null,
                filterValue = null,
                filterHeadKey = null,
                filterHeadValue = null,
                type = ConsumerFilterType.CONTAINS
            )
            val testRecord = record(value = "any-value")
            assertTrue(filter.isRecordPassFilter(testRecord))
        }

        @Test
        fun `should not pass when record value is null and filter value is set with CONTAINS`() {
            val filter = ConsumerFilter(
                filterKey = null,
                filterValue = "test",
                filterHeadKey = null,
                filterHeadValue = null,
                type = ConsumerFilterType.CONTAINS
            )
            val testRecord = record(value = null)
            assertFalse(filter.isRecordPassFilter(testRecord))
        }

        @Test
        fun `should handle non-string value with toString`() {
            val filter = ConsumerFilter(
                filterKey = null,
                filterValue = "123",
                filterHeadKey = null,
                filterHeadValue = null,
                type = ConsumerFilterType.CONTAINS
            )
            val testRecord = record(value = 12345)
            assertTrue(filter.isRecordPassFilter(testRecord))
        }
    }

    @Nested
    @DisplayName("Header key filtering")
    inner class HeaderKeyFiltering {

        @Test
        fun `should pass when header key contains filter value`() {
            val headers = RecordHeaders()
            headers.add("content-type", "application/json".toByteArray())
            headers.add("user-id", "123".toByteArray())
            val filter = ConsumerFilter(
                filterKey = null, filterValue = null,
                filterHeadKey = "content", filterHeadValue = null,
                type = ConsumerFilterType.CONTAINS
            )
            assertTrue(filter.isRecordPassFilter(record(headers = headers)))
        }

        @Test
        fun `should not pass when no header key contains filter value`() {
            val headers = RecordHeaders()
            headers.add("content-type", "application/json".toByteArray())
            val filter = ConsumerFilter(
                filterKey = null, filterValue = null,
                filterHeadKey = "missing", filterHeadValue = null,
                type = ConsumerFilterType.CONTAINS
            )
            assertFalse(filter.isRecordPassFilter(record(headers = headers)))
        }

        @Test
        fun `should pass when no header key contains filter value with DOES_NOT_CONTAINS`() {
            val headers = RecordHeaders()
            headers.add("content-type", "application/json".toByteArray())
            val filter = ConsumerFilter(
                filterKey = null, filterValue = null,
                filterHeadKey = "missing", filterHeadValue = null,
                type = ConsumerFilterType.DOES_NOT_CONTAINS
            )
            assertTrue(filter.isRecordPassFilter(record(headers = headers)))
        }

        @Test
        fun `should not pass when any header key contains filter value with DOES_NOT_CONTAINS`() {
            val headers = RecordHeaders()
            headers.add("content-type", "application/json".toByteArray())
            val filter = ConsumerFilter(
                filterKey = null, filterValue = null,
                filterHeadKey = "content", filterHeadValue = null,
                type = ConsumerFilterType.DOES_NOT_CONTAINS
            )
            assertFalse(filter.isRecordPassFilter(record(headers = headers)))
        }

        @Test
        fun `should pass when header key matches regex pattern`() {
            val headers = RecordHeaders()
            headers.add("user-id", "123".toByteArray())
            val filter = ConsumerFilter(
                filterKey = null, filterValue = null,
                filterHeadKey = "user-.*", filterHeadValue = null,
                type = ConsumerFilterType.REGEX
            )
            assertTrue(filter.isRecordPassFilter(record(headers = headers)))
        }

        @Test
        fun `should pass when filter header key is null`() {
            val headers = RecordHeaders()
            headers.add("content-type", "application/json".toByteArray())
            val filter = ConsumerFilter(
                filterKey = null, filterValue = null,
                filterHeadKey = null, filterHeadValue = null,
                type = ConsumerFilterType.CONTAINS
            )
            assertTrue(filter.isRecordPassFilter(record(headers = headers)))
        }

        @Test
        fun `should not pass when record has no headers and filter header key is set with CONTAINS`() {
            val filter = ConsumerFilter(
                filterKey = null, filterValue = null,
                filterHeadKey = "test", filterHeadValue = null,
                type = ConsumerFilterType.CONTAINS
            )
            assertFalse(filter.isRecordPassFilter(record(headers = RecordHeaders())))
        }
    }

    @Nested
    @DisplayName("Header value filtering")
    inner class HeaderValueFiltering {

        @Test
        fun `should pass when header value contains filter value`() {
            val headers = RecordHeaders()
            headers.add("content-type", "application/json".toByteArray())
            val filter = ConsumerFilter(
                filterKey = null, filterValue = null,
                filterHeadKey = null, filterHeadValue = "application",
                type = ConsumerFilterType.CONTAINS
            )
            assertTrue(filter.isRecordPassFilter(record(headers = headers)))
        }

        @Test
        fun `should not pass when no header value contains filter value`() {
            val headers = RecordHeaders()
            headers.add("content-type", "application/json".toByteArray())
            val filter = ConsumerFilter(
                filterKey = null, filterValue = null,
                filterHeadKey = null, filterHeadValue = "missing",
                type = ConsumerFilterType.CONTAINS
            )
            assertFalse(filter.isRecordPassFilter(record(headers = headers)))
        }

        @Test
        fun `should pass when no header value contains filter value with DOES_NOT_CONTAINS`() {
            val headers = RecordHeaders()
            headers.add("content-type", "application/json".toByteArray())
            val filter = ConsumerFilter(
                filterKey = null, filterValue = null,
                filterHeadKey = null, filterHeadValue = "missing",
                type = ConsumerFilterType.DOES_NOT_CONTAINS
            )
            assertTrue(filter.isRecordPassFilter(record(headers = headers)))
        }

        @Test
        fun `should not pass when any header value contains filter value with DOES_NOT_CONTAINS`() {
            val headers = RecordHeaders()
            headers.add("content-type", "application/json".toByteArray())
            val filter = ConsumerFilter(
                filterKey = null, filterValue = null,
                filterHeadKey = null, filterHeadValue = "json",
                type = ConsumerFilterType.DOES_NOT_CONTAINS
            )
            assertFalse(filter.isRecordPassFilter(record(headers = headers)))
        }

        @Test
        fun `should pass when header value matches regex pattern`() {
            val headers = RecordHeaders()
            headers.add("content-type", "application/json".toByteArray())
            val filter = ConsumerFilter(
                filterKey = null, filterValue = null,
                filterHeadKey = null, filterHeadValue = "application/.*",
                type = ConsumerFilterType.REGEX
            )
            assertTrue(filter.isRecordPassFilter(record(headers = headers)))
        }

        @Test
        fun `should pass when filter header value is null`() {
            val headers = RecordHeaders()
            headers.add("content-type", "application/json".toByteArray())
            val filter = ConsumerFilter(
                filterKey = null, filterValue = null,
                filterHeadKey = null, filterHeadValue = null,
                type = ConsumerFilterType.CONTAINS
            )
            assertTrue(filter.isRecordPassFilter(record(headers = headers)))
        }

        @Test
        fun `should handle byte array decoding in header values`() {
            val headers = RecordHeaders()
            headers.add("custom-header", "special-value-456".toByteArray())
            val filter = ConsumerFilter(
                filterKey = null, filterValue = null,
                filterHeadKey = null, filterHeadValue = "456",
                type = ConsumerFilterType.CONTAINS
            )
            assertTrue(filter.isRecordPassFilter(record(headers = headers)))
        }
    }

    @Nested
    @DisplayName("Combined filtering")
    inner class CombinedFiltering {

        @Test
        fun `should pass when all filters match`() {
            val headers = RecordHeaders()
            headers.add("content-type", "application/json".toByteArray())
            val filter = ConsumerFilter(
                filterKey = "test", filterValue = "data",
                filterHeadKey = "content", filterHeadValue = "json",
                type = ConsumerFilterType.CONTAINS
            )
            val testRecord = record(key = "test-key", value = "some-data", headers = headers)
            assertTrue(filter.isRecordPassFilter(testRecord))
        }

        @Test
        fun `should not pass when key filter fails`() {
            val headers = RecordHeaders()
            headers.add("content-type", "application/json".toByteArray())
            val filter = ConsumerFilter(
                filterKey = "missing", filterValue = "data",
                filterHeadKey = "content", filterHeadValue = "json",
                type = ConsumerFilterType.CONTAINS
            )
            val testRecord = record(key = "test-key", value = "some-data", headers = headers)
            assertFalse(filter.isRecordPassFilter(testRecord))
        }

        @Test
        fun `should not pass when value filter fails`() {
            val headers = RecordHeaders()
            headers.add("content-type", "application/json".toByteArray())
            val filter = ConsumerFilter(
                filterKey = "test", filterValue = "missing",
                filterHeadKey = "content", filterHeadValue = "json",
                type = ConsumerFilterType.CONTAINS
            )
            val testRecord = record(key = "test-key", value = "some-data", headers = headers)
            assertFalse(filter.isRecordPassFilter(testRecord))
        }

        @Test
        fun `should not pass when header key filter fails`() {
            val headers = RecordHeaders()
            headers.add("content-type", "application/json".toByteArray())
            val filter = ConsumerFilter(
                filterKey = "test", filterValue = "data",
                filterHeadKey = "missing", filterHeadValue = "json",
                type = ConsumerFilterType.CONTAINS
            )
            val testRecord = record(key = "test-key", value = "some-data", headers = headers)
            assertFalse(filter.isRecordPassFilter(testRecord))
        }

        @Test
        fun `should not pass when header value filter fails`() {
            val headers = RecordHeaders()
            headers.add("content-type", "application/json".toByteArray())
            val filter = ConsumerFilter(
                filterKey = "test", filterValue = "data",
                filterHeadKey = "content", filterHeadValue = "missing",
                type = ConsumerFilterType.CONTAINS
            )
            val testRecord = record(key = "test-key", value = "some-data", headers = headers)
            assertFalse(filter.isRecordPassFilter(testRecord))
        }
    }
}
