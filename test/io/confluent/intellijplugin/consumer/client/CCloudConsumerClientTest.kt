package io.confluent.intellijplugin.consumer.client

import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.ccloud.fetcher.DataPlaneFetcher
import io.confluent.intellijplugin.ccloud.model.response.SchemaByIdResponse
import io.confluent.intellijplugin.common.models.KafkaFieldType
import io.confluent.intellijplugin.consumer.models.ConsumerProducerFieldConfig
import io.confluent.intellijplugin.data.CCloudClusterDataManager
import io.confluent.intellijplugin.registry.KafkaRegistryFormat
import io.confluent.intellijplugin.registry.KafkaRegistryType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.io.EncoderFactory
import io.confluent.kafka.serializers.schema.id.SchemaId
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.VoidDeserializer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.Base64
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
                RecordHeader(SchemaId.VALUE_SCHEMA_ID_HEADER, buildV1HeaderValue(guid))
            ))
            val result = client.getSchemaGuidFromHeaders(headers, isKey = false)
            assertEquals(guid, result)
        }

        @Test
        fun `should extract UUID from key schema header`() {
            val guid = UUID.randomUUID()
            val headers = RecordHeaders(listOf(
                RecordHeader(SchemaId.KEY_SCHEMA_ID_HEADER, buildV1HeaderValue(guid))
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
                RecordHeader(SchemaId.VALUE_SCHEMA_ID_HEADER, byteArrayOf(0x01, 0x02))
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
                RecordHeader(SchemaId.VALUE_SCHEMA_ID_HEADER, buffer.array())
            ))
            val result = client.getSchemaGuidFromHeaders(headers, isKey = false)
            assertNull(result)
        }
    }

    @Nested
    @DisplayName("Avro deserialization")
    inner class AvroDeserialization {

        @Test
        fun `should deserialize V0 Avro payload to GenericRecord`() = runBlocking {
            val schemaId = 1
            val avroPayload = encodeAvroPayload(avroSchemaJson, "Alice", 30)
            val wireBytes = buildV0Payload(schemaId, avroPayload)
            val headers = RecordHeaders()

            whenever(mockFetcher.getSchemaIdInfo(schemaId)).thenReturn(
                SchemaByIdResponse(schema = avroSchemaJson, schemaType = "AVRO")
            )

            val result = client.deserializeSchemaEncoded(wireBytes, mockFetcher, headers, isKey = false)

            assertTrue(result is GenericData.Record, "Expected GenericData.Record but got ${result::class.simpleName}")
            val record = result as GenericData.Record
            assertEquals("Alice", record.get("name").toString())
            assertEquals(30, record.get("age"))
        }

        @Test
        fun `should deserialize V1 Avro payload from header GUID`() = runBlocking {
            val guid = UUID.randomUUID()
            val avroPayload = encodeAvroPayload(avroSchemaJson, "Bob", 25)
            // V1: no prefix on payload, schema ID in header
            val headers = RecordHeaders(listOf(
                RecordHeader(SchemaId.VALUE_SCHEMA_ID_HEADER, buildV1HeaderValue(guid))
            ))

            whenever(mockFetcher.getSchemaByGuid(guid.toString())).thenReturn(
                SchemaByIdResponse(schema = avroSchemaJson, schemaType = "AVRO")
            )

            val result = client.deserializeSchemaEncoded(avroPayload, mockFetcher, headers, isKey = false)

            assertTrue(result is GenericData.Record, "Expected GenericData.Record but got ${result::class.simpleName}")
            val record = result as GenericData.Record
            assertEquals("Bob", record.get("name").toString())
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

            assertTrue(result is GenericData.Record, "Expected GenericData.Record but got ${result::class.simpleName}")
            val record = result as GenericData.Record
            assertEquals("Carol", record.get("name").toString())
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
                RecordHeader(SchemaId.VALUE_SCHEMA_ID_HEADER, buildV1HeaderValue(guid))
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
        fun `should throw when schema fetch fails`() = runBlocking {
            val schemaId = 10
            val wireBytes = buildV0Payload(schemaId, byteArrayOf(1, 2, 3))
            val headers = RecordHeaders()

            whenever(mockFetcher.getSchemaIdInfo(schemaId))
                .thenThrow(RuntimeException("Schema Registry unavailable"))

            assertThrows(RuntimeException::class.java) {
                runBlocking { client.deserializeSchemaEncoded(wireBytes, mockFetcher, headers, isKey = false) }
            }
        }

        @Test
        fun `should throw when deserialization fails`() = runBlocking {
            val schemaId = 11
            // Garbage payload that can't be decoded as Avro
            val wireBytes = buildV0Payload(schemaId, byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
            val headers = RecordHeaders()

            whenever(mockFetcher.getSchemaIdInfo(schemaId)).thenReturn(
                SchemaByIdResponse(schema = avroSchemaJson, schemaType = "AVRO")
            )

            assertThrows(Exception::class.java) {
                runBlocking { client.deserializeSchemaEncoded(wireBytes, mockFetcher, headers, isKey = false) }
            }
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
                RecordHeader(SchemaId.VALUE_SCHEMA_ID_HEADER, rawHeaderValue)
            ))

            val result = client.getSchemaGuidFromHeaders(headers, isKey = false)
            assertEquals(guid, result)
        }

        @Test
        fun `should handle key and value schema headers independently`() {
            val keyGuid = UUID.randomUUID()
            val valueGuid = UUID.randomUUID()

            val headers = RecordHeaders(listOf(
                RecordHeader(SchemaId.KEY_SCHEMA_ID_HEADER, buildV1HeaderValue(keyGuid)),
                RecordHeader(SchemaId.VALUE_SCHEMA_ID_HEADER, buildV1HeaderValue(valueGuid))
            ))

            assertEquals(keyGuid, client.getSchemaGuidFromHeaders(headers, isKey = true))
            assertEquals(valueGuid, client.getSchemaGuidFromHeaders(headers, isKey = false))
        }
    }

    // ── Helper to build a ConsumerProducerFieldConfig with a specific type ──

    private fun fieldConfig(type: KafkaFieldType, isKey: Boolean = false) = ConsumerProducerFieldConfig(
        type = type,
        valueText = "",
        isKey = isKey,
        topic = "test-topic",
        registryType = KafkaRegistryType.NONE,
        schemaName = "",
        schemaFormat = KafkaRegistryFormat.AVRO,
        parsedSchema = null
    )

    @Nested
    @DisplayName("Primitive type conversion - convertJsonToType")
    inner class PrimitiveTypeConversion {

        @Test
        fun `should convert string primitive to LONG`() {
            val result = client.convertJsonToType(JsonPrimitive("12345"), KafkaFieldType.LONG)
            assertEquals(12345L, result)
        }

        @Test
        fun `should throw for non-numeric LONG`() {
            assertThrows(SerializationException::class.java) {
                client.convertJsonToType(JsonPrimitive("not-a-number"), KafkaFieldType.LONG)
            }
        }

        @Test
        fun `should convert string primitive to INTEGER`() {
            val result = client.convertJsonToType(JsonPrimitive("42"), KafkaFieldType.INTEGER)
            assertEquals(42, result)
        }

        @Test
        fun `should throw for non-numeric INTEGER`() {
            assertThrows(SerializationException::class.java) {
                client.convertJsonToType(JsonPrimitive("abc"), KafkaFieldType.INTEGER)
            }
        }

        @Test
        fun `should convert string primitive to DOUBLE`() {
            val result = client.convertJsonToType(JsonPrimitive("3.14"), KafkaFieldType.DOUBLE)
            assertEquals(3.14, result)
        }

        @Test
        fun `should convert string primitive to FLOAT`() {
            val result = client.convertJsonToType(JsonPrimitive("2.5"), KafkaFieldType.FLOAT)
            assertEquals(2.5f, result)
        }

        @Test
        fun `should decode valid BASE64 to ByteArray`() {
            val original = "hello".toByteArray()
            val encoded = Base64.getEncoder().encodeToString(original)
            val result = client.convertJsonToType(JsonPrimitive(encoded), KafkaFieldType.BASE64)
            assertArrayEquals(original, result as ByteArray)
        }

        @Test
        fun `should throw for invalid BASE64`() {
            assertThrows(IllegalArgumentException::class.java) {
                client.convertJsonToType(JsonPrimitive("not-base64!!!"), KafkaFieldType.BASE64)
            }
        }

        @Test
        fun `should return null for NULL type`() {
            val result = client.convertJsonToType(JsonPrimitive("anything"), KafkaFieldType.NULL)
            assertNull(result)
        }

        @Test
        fun `should return null for JsonNull`() {
            val result = client.convertJsonToType(JsonNull, KafkaFieldType.STRING)
            assertNull(result)
        }

        @Test
        fun `should return string content for STRING type`() {
            val result = client.convertJsonToType(JsonPrimitive("hello world"), KafkaFieldType.STRING)
            assertEquals("hello world", result)
        }

        @Test
        fun `should return JSON string for JSON type`() {
            val result = client.convertJsonToType(JsonPrimitive("value"), KafkaFieldType.JSON)
            // JSON type returns element.toString() which wraps strings in quotes
            assertEquals("\"value\"", result)
        }

        @Test
        fun `should throw for JsonObject with numeric type`() {
            val obj = JsonObject(mapOf("a" to JsonPrimitive(1)))
            assertThrows(SerializationException::class.java) {
                client.convertJsonToType(obj, KafkaFieldType.LONG)
            }
        }
    }

    @Nested
    @DisplayName("Raw bytes type conversion - convertBytesToType")
    inner class RawBytesTypeConversion {

        @Test
        fun `should read 8-byte big-endian LONG`() {
            val bytes = ByteBuffer.allocate(8).putLong(123456789L).array()
            val result = client.convertBytesToType(bytes, "test-topic", KafkaFieldType.LONG)
            assertEquals(123456789L, result)
        }

        @Test
        fun `should throw SerializationException for non-8-byte LONG`() {
            // Matches native consumer: LongDeserializer throws on wrong byte length
            val bytes = "42".toByteArray()
            assertThrows(org.apache.kafka.common.errors.SerializationException::class.java) {
                client.convertBytesToType(bytes, "test-topic", KafkaFieldType.LONG)
            }
        }

        @Test
        fun `should read 4-byte big-endian INTEGER`() {
            val bytes = ByteBuffer.allocate(4).putInt(42).array()
            val result = client.convertBytesToType(bytes, "test-topic", KafkaFieldType.INTEGER)
            assertEquals(42, result)
        }

        @Test
        fun `should read 8-byte DOUBLE`() {
            val bytes = ByteBuffer.allocate(8).putDouble(3.14).array()
            val result = client.convertBytesToType(bytes, "test-topic", KafkaFieldType.DOUBLE)
            assertEquals(3.14, result)
        }

        @Test
        fun `should read 4-byte FLOAT`() {
            val bytes = ByteBuffer.allocate(4).putFloat(2.5f).array()
            val result = client.convertBytesToType(bytes, "test-topic", KafkaFieldType.FLOAT)
            assertEquals(2.5f, result)
        }

        @Test
        fun `should return raw bytes for BASE64`() {
            val bytes = byteArrayOf(1, 2, 3)
            val result = client.convertBytesToType(bytes, "test-topic", KafkaFieldType.BASE64)
            assertArrayEquals(bytes, result as ByteArray)
        }

        @Test
        fun `should return null for NULL type with null data`() {
            val deserializer = VoidDeserializer()
            val result = deserializer.deserialize("test-topic", null)
            assertNull(result)
        }

        @Test
        fun `should throw for NULL type with non-null data`() {
            val bytes = byteArrayOf(1, 2)
            assertThrows(IllegalArgumentException::class.java) {
                client.convertBytesToType(bytes, "test-topic", KafkaFieldType.NULL)
            }
        }

        @Test
        fun `should return UTF-8 string for STRING type`() {
            val result = client.convertBytesToType("hello".toByteArray(), "test-topic", KafkaFieldType.STRING)
            assertEquals("hello", result)
        }
    }

    @Nested
    @DisplayName("Type selector integration")
    inner class TypeSelectorIntegration {

        @Test
        fun `should deserialize schema-encoded values only when SCHEMA_REGISTRY is selected`() = runBlocking {
            client.currentValueConfig = fieldConfig(KafkaFieldType.SCHEMA_REGISTRY)

            val schemaId = 1
            val avroPayload = encodeAvroPayload(avroSchemaJson, "TypeTest", 50)
            val wireBytes = buildV0Payload(schemaId, avroPayload)
            val headers = RecordHeaders()

            whenever(mockFetcher.getSchemaIdInfo(schemaId)).thenReturn(
                SchemaByIdResponse(schema = avroSchemaJson, schemaType = "AVRO")
            )

            val result = client.deserializeSchemaEncoded(wireBytes, mockFetcher, headers, isKey = false)
            assertTrue(result is GenericData.Record, "Expected GenericData.Record but got ${result::class.simpleName}")
            assertEquals("TypeTest", (result as GenericData.Record).get("name").toString())
        }

        @Test
        fun `should treat binary data as UTF-8 string when STRING is selected`() {
            val schemaId = 1
            val avroPayload = encodeAvroPayload(avroSchemaJson, "Alice", 30)
            val wireBytes = buildV0Payload(schemaId, avroPayload)

            val result = client.convertBytesToType(wireBytes, "test-topic", KafkaFieldType.STRING)

            assertTrue(result is String, "Expected String but got ${result!!::class.simpleName}")
            // No schema deserialization should have occurred
            verifyNoInteractions(mockFetcher)
        }
    }
}
