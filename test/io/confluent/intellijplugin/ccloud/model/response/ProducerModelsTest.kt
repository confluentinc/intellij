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
            assertEquals(1709654400000L, response.timestamp)
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
                    "timestamp": 100,
                    "unknown_field": "should be ignored"
                }
            """.trimIndent()

            val response = json.decodeFromString<ProduceRecordResponse>(responseJson)
            assertEquals(200, response.errorCode)
            assertEquals(1, response.offset)
        }
    }

    private fun loadFixture(path: String): String {
        return javaClass.getResourceAsStream(path)!!.readBytes().toString(Charsets.UTF_8)
    }
}
