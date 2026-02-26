package io.confluent.intellijplugin.consumer.client

import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.ccloud.fetcher.DataPlaneFetcher
import io.confluent.intellijplugin.ccloud.model.response.SchemaByIdResponse
import io.confluent.intellijplugin.data.CCloudClusterDataManager
import kotlinx.coroutines.runBlocking
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.io.EncoderFactory
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.header.internals.RecordHeaders
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.UUID

@TestApplication
class CCloudConsumerClientTest {

    private lateinit var mockDataManager: CCloudClusterDataManager
    private lateinit var mockFetcher: DataPlaneFetcher
    private lateinit var client: CCloudConsumerClient

    private val avroSchemaJson = javaClass.getResourceAsStream(
        "/fixtures/schemas/user-avro-schema.json"
    )!!.readBytes().toString(Charsets.UTF_8)

    private val jsonSchemaText = javaClass.getResourceAsStream(
        "/fixtures/schemas/user-json-schema.json"
    )!!.readBytes().toString(Charsets.UTF_8)

    @BeforeEach
    fun setUp() {
        mockDataManager = mock()
        mockFetcher = mock()
        client = CCloudConsumerClient(
            clusterDataManager = mockDataManager,
            onStart = {},
            onStop = {}
        )
    }

    // ── Helper methods ──────────────────────────────────────────────────

    /** Build V0 wire-format bytes: magic 0x00 + 4-byte schema ID + payload. */
    private fun buildV0Payload(schemaId: Int, payload: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(5 + payload.size)
        buffer.put(0x00) // magic byte V0
        buffer.putInt(schemaId)
        buffer.put(payload)
        return buffer.array()
    }

    /** Build V1 header value: magic 0x01 + 16-byte UUID. */
    private fun buildV1HeaderValue(guid: UUID): ByteArray {
        val buffer = ByteBuffer.allocate(17)
        buffer.put(0x01) // magic byte V1
        buffer.putLong(guid.mostSignificantBits)
        buffer.putLong(guid.leastSignificantBits)
        return buffer.array()
    }

    /** Encode an Avro record as binary bytes (without wire format prefix). */
    private fun encodeAvroPayload(schemaJson: String, name: String, age: Int): ByteArray {
        val schema = Schema.Parser().parse(schemaJson)
        val record = GenericData.Record(schema).apply {
            put("name", name)
            put("age", age)
        }
        val out = ByteArrayOutputStream()
        val writer = GenericDatumWriter<Any>(schema)
        val encoder = EncoderFactory.get().binaryEncoder(out, null)
        writer.write(record, encoder)
        encoder.flush()
        return out.toByteArray()
    }

    @Nested
    @DisplayName("Wire format detection - V0 (payload prefix)")
    inner class V0Detection {

        @Test
        fun `should extract schema ID from V0 payload`() {
            val payload = buildV0Payload(schemaId = 42, payload = byteArrayOf(1, 2, 3))
            val result = client.getSchemaIdFromRawBytes(payload)
            assertEquals(42, result)
        }

        @Test
        fun `should return null for payload shorter than 5 bytes`() {
            val result = client.getSchemaIdFromRawBytes(byteArrayOf(0x00, 0x01))
            assertNull(result)
        }

        @Test
        fun `should return null when magic byte is not 0x00`() {
            val payload = ByteBuffer.allocate(5).put(0x01).putInt(42).array()
            val result = client.getSchemaIdFromRawBytes(payload)
            assertNull(result)
        }

        @Test
        fun `should handle large schema IDs`() {
            val payload = buildV0Payload(schemaId = 100_000, payload = byteArrayOf())
            val result = client.getSchemaIdFromRawBytes(payload)
            assertEquals(100_000, result)
        }
    }

    @Nested
    @DisplayName("Wire format detection - V1 (header GUID)")
    inner class V1Detection {

        @Test
        fun `should extract UUID from value schema header`() {
            val guid = UUID.randomUUID()
            val headers = RecordHeaders(listOf(
                RecordHeader("confluent.value.schemaId", buildV1HeaderValue(guid))
            ))
            val result = client.getSchemaGuidFromHeaders(headers, isKey = false)
            assertEquals(guid, result)
        }

        @Test
        fun `should extract UUID from key schema header`() {
            val guid = UUID.randomUUID()
            val headers = RecordHeaders(listOf(
                RecordHeader("confluent.key.schemaId", buildV1HeaderValue(guid))
            ))
            val result = client.getSchemaGuidFromHeaders(headers, isKey = true)
            assertEquals(guid, result)
        }

        @Test
        fun `should return null when header is missing`() {
            val headers = RecordHeaders()
            val result = client.getSchemaGuidFromHeaders(headers, isKey = false)
            assertNull(result)
        }

        @Test
        fun `should return null when header value is too short`() {
            val headers = RecordHeaders(listOf(
                RecordHeader("confluent.value.schemaId", byteArrayOf(0x01, 0x02))
            ))
            val result = client.getSchemaGuidFromHeaders(headers, isKey = false)
            assertNull(result)
        }

        @Test
        fun `should return null when header magic byte is not 0x01`() {
            val buffer = ByteBuffer.allocate(17)
            buffer.put(0x00) // wrong magic byte
            buffer.putLong(0L)
            buffer.putLong(0L)
            val headers = RecordHeaders(listOf(
                RecordHeader("confluent.value.schemaId", buffer.array())
            ))
            val result = client.getSchemaGuidFromHeaders(headers, isKey = false)
            assertNull(result)
        }
    }

    @Nested
    @DisplayName("Avro deserialization")
    inner class AvroDeserialization {

        @Test
        fun `should deserialize V0 Avro payload to JSON`() = runBlocking {
            val schemaId = 1
            val avroPayload = encodeAvroPayload(avroSchemaJson, "Alice", 30)
            val wireBytes = buildV0Payload(schemaId, avroPayload)
            val headers = RecordHeaders()

            whenever(mockFetcher.getSchemaIdInfo(schemaId)).thenReturn(
                SchemaByIdResponse(schema = avroSchemaJson, schemaType = "AVRO")
            )

            val result = client.deserializeSchemaEncoded(wireBytes, mockFetcher, headers, isKey = false)

            assertTrue(result is String, "Expected String but got ${result::class.simpleName}")
            val json = result as String
            assertTrue(json.contains("Alice"), "Expected JSON to contain 'Alice': $json")
            assertTrue(json.contains("30"), "Expected JSON to contain '30': $json")
        }

        @Test
        fun `should deserialize V1 Avro payload from header GUID`() = runBlocking {
            val guid = UUID.randomUUID()
            val avroPayload = encodeAvroPayload(avroSchemaJson, "Bob", 25)
            // V1: no prefix on payload, schema ID in header
            val headers = RecordHeaders(listOf(
                RecordHeader("confluent.value.schemaId", buildV1HeaderValue(guid))
            ))

            whenever(mockFetcher.getSchemaByGuid(guid.toString())).thenReturn(
                SchemaByIdResponse(schema = avroSchemaJson, schemaType = "AVRO")
            )

            val result = client.deserializeSchemaEncoded(avroPayload, mockFetcher, headers, isKey = false)

            assertTrue(result is String, "Expected String but got ${result::class.simpleName}")
            val json = result as String
            assertTrue(json.contains("Bob"), "Expected JSON to contain 'Bob': $json")
        }

        @Test
        fun `should default to AVRO when schemaType is null`() = runBlocking {
            val schemaId = 2
            val avroPayload = encodeAvroPayload(avroSchemaJson, "Carol", 22)
            val wireBytes = buildV0Payload(schemaId, avroPayload)
            val headers = RecordHeaders()

            // SR convention: null schemaType means AVRO
            whenever(mockFetcher.getSchemaIdInfo(schemaId)).thenReturn(
                SchemaByIdResponse(schema = avroSchemaJson, schemaType = null)
            )

            val result = client.deserializeSchemaEncoded(wireBytes, mockFetcher, headers, isKey = false)

            assertTrue(result is String, "Expected String but got ${result::class.simpleName}")
            val json = result as String
            assertTrue(json.contains("Carol"), "Expected JSON to contain 'Carol': $json")
        }
    }

    @Nested
    @DisplayName("JSON Schema deserialization")
    inner class JsonSchemaDeserialization {

        @Test
        fun `should pass through JSON Schema payload as UTF-8 string`() = runBlocking {
            val schemaId = 3
            val jsonPayload = """{"name":"Charlie","age":40}""".toByteArray()
            val wireBytes = buildV0Payload(schemaId, jsonPayload)
            val headers = RecordHeaders()

            whenever(mockFetcher.getSchemaIdInfo(schemaId)).thenReturn(
                SchemaByIdResponse(schema = jsonSchemaText, schemaType = "JSON")
            )

            val result = client.deserializeSchemaEncoded(wireBytes, mockFetcher, headers, isKey = false)

            assertEquals("""{"name":"Charlie","age":40}""", result)
        }
    }

    @Nested
    @DisplayName("Priority and fallback behavior")
    inner class PriorityAndFallback {

        @Test
        fun `should prefer V1 header GUID over V0 payload ID`() = runBlocking {
            val guid = UUID.randomUUID()
            val v0SchemaId = 99

            val avroPayload = encodeAvroPayload(avroSchemaJson, "Diana", 35)
            val wireBytes = buildV0Payload(v0SchemaId, avroPayload)

            // Header has V1 GUID
            val headers = RecordHeaders(listOf(
                RecordHeader("confluent.value.schemaId", buildV1HeaderValue(guid))
            ))

            whenever(mockFetcher.getSchemaByGuid(guid.toString())).thenReturn(
                SchemaByIdResponse(schema = avroSchemaJson, schemaType = "AVRO")
            )

            client.deserializeSchemaEncoded(wireBytes, mockFetcher, headers, isKey = false)

            // V1 (GUID) should be used, NOT V0 (numeric ID)
            verify(mockFetcher).getSchemaByGuid(guid.toString())
            verify(mockFetcher, never()).getSchemaIdInfo(any())
        }

        @Test
        fun `should return raw bytes when no schema info is detected`() = runBlocking {
            val rawBytes = byteArrayOf(0x05, 0x06, 0x07, 0x08)
            val headers = RecordHeaders()

            val result = client.deserializeSchemaEncoded(rawBytes, mockFetcher, headers, isKey = false)

            assertTrue(result is ByteArray, "Expected ByteArray but got ${result::class.simpleName}")
            assertArrayEquals(rawBytes, result as ByteArray)
            verifyNoInteractions(mockFetcher)
        }

        @Test
        fun `should fall back to raw bytes when schema fetch fails`() = runBlocking {
            val schemaId = 10
            val wireBytes = buildV0Payload(schemaId, byteArrayOf(1, 2, 3))
            val headers = RecordHeaders()

            whenever(mockFetcher.getSchemaIdInfo(schemaId))
                .thenThrow(RuntimeException("Schema Registry unavailable"))

            val result = client.deserializeSchemaEncoded(wireBytes, mockFetcher, headers, isKey = false)

            assertTrue(result is ByteArray, "Expected ByteArray fallback but got ${result::class.simpleName}")
            assertArrayEquals(wireBytes, result as ByteArray)
        }

        @Test
        fun `should fall back to raw bytes when deserialization fails`() = runBlocking {
            val schemaId = 11
            // Garbage payload that can't be decoded as Avro
            val wireBytes = buildV0Payload(schemaId, byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
            val headers = RecordHeaders()

            whenever(mockFetcher.getSchemaIdInfo(schemaId)).thenReturn(
                SchemaByIdResponse(schema = avroSchemaJson, schemaType = "AVRO")
            )

            val result = client.deserializeSchemaEncoded(wireBytes, mockFetcher, headers, isKey = false)

            assertTrue(result is ByteArray, "Expected ByteArray fallback but got ${result::class.simpleName}")
        }
    }

    @Nested
    @DisplayName("Schema caching")
    inner class SchemaCaching {

        @Test
        fun `should cache schema and not re-fetch for same schema ID`() = runBlocking {
            val schemaId = 5
            val avroPayload = encodeAvroPayload(avroSchemaJson, "Eve", 28)
            val wireBytes = buildV0Payload(schemaId, avroPayload)
            val headers = RecordHeaders()

            whenever(mockFetcher.getSchemaIdInfo(schemaId)).thenReturn(
                SchemaByIdResponse(schema = avroSchemaJson, schemaType = "AVRO")
            )

            // Deserialize twice with the same schema ID
            client.deserializeSchemaEncoded(wireBytes, mockFetcher, headers, isKey = false)
            client.deserializeSchemaEncoded(wireBytes, mockFetcher, headers, isKey = false)

            // Fetcher should only be called once due to caching
            verify(mockFetcher, times(1)).getSchemaIdInfo(schemaId)
        }
    }

    @Nested
    @DisplayName("Header base64 decoding")
    inner class HeaderBase64Decoding {

        @Test
        fun `should properly decode base64 header values for GUID extraction`() {
            val guid = UUID.randomUUID()
            val rawHeaderValue = buildV1HeaderValue(guid)

            val headers = RecordHeaders(listOf(
                RecordHeader("confluent.value.schemaId", rawHeaderValue)
            ))

            val result = client.getSchemaGuidFromHeaders(headers, isKey = false)
            assertEquals(guid, result)
        }

        @Test
        fun `should handle key and value schema headers independently`() {
            val keyGuid = UUID.randomUUID()
            val valueGuid = UUID.randomUUID()

            val headers = RecordHeaders(listOf(
                RecordHeader("confluent.key.schemaId", buildV1HeaderValue(keyGuid)),
                RecordHeader("confluent.value.schemaId", buildV1HeaderValue(valueGuid))
            ))

            assertEquals(keyGuid, client.getSchemaGuidFromHeaders(headers, isKey = true))
            assertEquals(valueGuid, client.getSchemaGuidFromHeaders(headers, isKey = false))
        }
    }
}
