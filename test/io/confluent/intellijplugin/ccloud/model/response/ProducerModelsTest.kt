package io.confluent.intellijplugin.ccloud.model.response

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ProducerModels")
class ProducerModelsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Nested
    @DisplayName("ProduceRecordRequest serialization")
    inner class RequestSerialization {

        @Test
        fun `should serialize string request with headers and partition`() {
            val request = ProduceRecordRequest(
                partitionId = 0,
                headers = listOf(
                    ProduceRecordHeader("correlation-id", "dGVzdC12YWx1ZQ==")
                ),
                key = ProduceRecordData(type = "STRING", data = "my-key"),
                value = ProduceRecordData(type = "STRING", data = "my-value")
            )

            val serialized = json.encodeToString(request)
            val expected = loadFixture("/fixtures/producer/produce-record-request.json")
            assertEquals(json.parseToJsonElement(expected), json.parseToJsonElement(serialized))
        }

        @Test
        fun `should serialize binary request`() {
            val request = ProduceRecordRequest(
                key = ProduceRecordData(type = "BINARY", data = "AAAAAAAAAAE="),
                value = ProduceRecordData(type = "BINARY", data = "SGVsbG8gV29ybGQ=")
            )

            val serialized = json.encodeToString(request)
            val expected = loadFixture("/fixtures/producer/produce-record-request-binary.json")
            assertEquals(json.parseToJsonElement(expected), json.parseToJsonElement(serialized))
        }

        @Test
        fun `should serialize request with null key`() {
            val request = ProduceRecordRequest(
                key = null,
                value = ProduceRecordData(type = "STRING", data = "only-value")
            )

            val serialized = json.encodeToString(request)
            val deserialized = json.decodeFromString<ProduceRecordRequest>(serialized)
            assertNull(deserialized.key)
            assertNotNull(deserialized.value)
            assertEquals("only-value", deserialized.value?.data)
        }

        @Test
        fun `should roundtrip request through serialization`() {
            val request = ProduceRecordRequest(
                partitionId = 3,
                headers = listOf(
                    ProduceRecordHeader("key1", "dmFsdWUx"),
                    ProduceRecordHeader("key2", null)
                ),
                key = ProduceRecordData(type = "JSON", data = """{"id": 1}"""),
                value = ProduceRecordData(type = "BINARY", data = "AQID"),
                timestamp = 1709654400000L
            )

            val serialized = json.encodeToString(request)
            val deserialized = json.decodeFromString<ProduceRecordRequest>(serialized)

            assertEquals(request, deserialized)
        }
    }

    @Nested
    @DisplayName("ProduceRecordData deserialization")
    inner class RecordDataDeserialization {

        @Test
        fun `should deserialize from JSON with type and data`() {
            val input = """{"type": "STRING", "data": "hello-world"}"""
            val data = json.decodeFromString<ProduceRecordData>(input)

            assertEquals("STRING", data.type)
            assertEquals("hello-world", data.data)
        }

        @Test
        fun `should deserialize with missing data field as null`() {
            val input = """{"type": "BINARY"}"""
            val data = json.decodeFromString<ProduceRecordData>(input)

            assertEquals("BINARY", data.type)
            assertNull(data.data)
        }

        @Test
        fun `should deserialize with explicit null data`() {
            val input = """{"type": "JSON", "data": null}"""
            val data = json.decodeFromString<ProduceRecordData>(input)

            assertEquals("JSON", data.type)
            assertNull(data.data)
        }
    }

    @Nested
    @DisplayName("ProduceRecordHeader deserialization")
    inner class RecordHeaderDeserialization {

        @Test
        fun `should deserialize from JSON with name and value`() {
            val input = """{"name": "correlation-id", "value": "dGVzdC12YWx1ZQ=="}"""
            val header = json.decodeFromString<ProduceRecordHeader>(input)

            assertEquals("correlation-id", header.name)
            assertEquals("dGVzdC12YWx1ZQ==", header.value)
        }

        @Test
        fun `should deserialize with missing value field as null`() {
            val input = """{"name": "trace-id"}"""
            val header = json.decodeFromString<ProduceRecordHeader>(input)

            assertEquals("trace-id", header.name)
            assertNull(header.value)
        }

        @Test
        fun `should deserialize with explicit null value`() {
            val input = """{"name": "empty-header", "value": null}"""
            val header = json.decodeFromString<ProduceRecordHeader>(input)

            assertEquals("empty-header", header.name)
            assertNull(header.value)
        }
    }

    @Nested
    @DisplayName("ProduceRecordResponseData deserialization")
    inner class ResponseDataDeserialization {

        @Test
        fun `should deserialize with all fields`() {
            val input = """{"size": 10, "type": "STRING"}"""
            val data = json.decodeFromString<ProduceRecordResponseData>(input)

            assertEquals(10, data.size)
            assertEquals("STRING", data.type)
        }

        @Test
        fun `should deserialize with missing fields as null`() {
            val input = """{}"""
            val data = json.decodeFromString<ProduceRecordResponseData>(input)

            assertNull(data.size)
            assertNull(data.type)
        }

        @Test
        fun `should deserialize with only size`() {
            val input = """{"size": 5}"""
            val data = json.decodeFromString<ProduceRecordResponseData>(input)

            assertEquals(5, data.size)
            assertNull(data.type)
        }
    }

    @Nested
    @DisplayName("ProduceRecordResponse deserialization")
    inner class ResponseDeserialization {

        @Test
        fun `should deserialize success response`() {
            val responseJson = loadFixture("/fixtures/producer/produce-record-response.json")
            val response = json.decodeFromString<ProduceRecordResponse>(responseJson)

            assertEquals(200, response.errorCode)
            assertEquals("lkc-test123", response.clusterId)
            assertEquals("test-topic", response.topicName)
            assertEquals(0, response.partitionId)
            assertEquals(42, response.offset)
            assertEquals("2024-03-05T16:00:00.000Z", response.timestamp)
            assertEquals(6, response.key?.size)
            assertEquals("STRING", response.key?.type)
            assertEquals(8, response.value?.size)
            assertEquals("STRING", response.value?.type)
        }

        @Test
        fun `should deserialize error response`() {
            val responseJson = loadFixture("/fixtures/producer/produce-record-response-error.json")
            val response = json.decodeFromString<ProduceRecordResponse>(responseJson)

            assertEquals(404, response.errorCode)
            assertEquals("This server does not host this topic-partition.", response.message)
            assertNull(response.clusterId)
            assertNull(response.topicName)
            assertNull(response.offset)
        }

        @Test
        fun `should handle unknown fields in response`() {
            val responseJson = """
                {
                    "error_code": 200,
                    "cluster_id": "lkc-123",
                    "topic_name": "t",
                    "partition_id": 0,
                    "offset": 1,
                    "timestamp": "2024-01-01T00:00:00.000Z",
                    "unknown_field": "should be ignored"
                }
            """.trimIndent()

            val response = json.decodeFromString<ProduceRecordResponse>(responseJson)
            assertEquals(200, response.errorCode)
            assertEquals(1, response.offset)
        }

        @Test
        fun `should roundtrip response through serialization`() {
            val response = ProduceRecordResponse(
                errorCode = 200,
                clusterId = "lkc-123",
                topicName = "topic-1",
                partitionId = 2,
                offset = 99,
                timestamp = "2024-03-05T16:00:00.000Z",
                key = ProduceRecordResponseData(size = 5, type = "STRING"),
                value = ProduceRecordResponseData(size = 10, type = "BINARY")
            )

            val serialized = json.encodeToString(response)
            val deserialized = json.decodeFromString<ProduceRecordResponse>(serialized)

            assertEquals(response, deserialized)
        }
    }

    private fun loadFixture(path: String): String {
        return javaClass.getResourceAsStream(path)!!.readBytes().toString(Charsets.UTF_8)
    }
}
