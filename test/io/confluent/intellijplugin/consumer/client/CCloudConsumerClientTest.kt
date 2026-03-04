package io.confluent.intellijplugin.consumer.client

import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.ccloud.fetcher.DataPlaneFetcher
import io.confluent.intellijplugin.ccloud.model.response.SchemaByIdResponse
import io.confluent.intellijplugin.common.models.KafkaFieldType
import io.confluent.intellijplugin.consumer.models.ConsumerProducerFieldConfig
import io.confluent.intellijplugin.data.CCloudClusterDataManager
import io.confluent.intellijplugin.registry.KafkaRegistryFormat
import io.confluent.intellijplugin.registry.KafkaRegistryType
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.protobuf.MessageIndexes
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema
import com.google.protobuf.DynamicMessage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.io.EncoderFactory
import io.confluent.kafka.serializers.schema.id.SchemaId
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.record.TimestampType
import org.apache.kafka.common.serialization.*
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

    // Fixtures

    private val avroSchemaJson = javaClass.getResourceAsStream(
        "/fixtures/schemas/user-avro-schema.json"
    )!!.readBytes().toString(Charsets.UTF_8)

    private val jsonSchemaText = javaClass.getResourceAsStream(
        "/fixtures/schemas/user-json-schema.json"
    )!!.readBytes().toString(Charsets.UTF_8)

    private val protoSchemaText = javaClass.getResourceAsStream(
        "/fixtures/schemas/user-proto-schema.proto"
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

    /** Build V0 wire-format bytes: magic 0x00 + 4-byte schema ID + payload. */
    private fun buildV0Payload(schemaId: Int, payload: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(5 + payload.size)
        buffer.put(SchemaId.MAGIC_BYTE_V0)
        buffer.putInt(schemaId)
        buffer.put(payload)
        return buffer.array()
    }

    /** Build V1 header value: magic 0x01 + 16-byte UUID. */
    private fun buildV1HeaderValue(guid: UUID): ByteArray {
        val buffer = ByteBuffer.allocate(17)
        buffer.put(SchemaId.MAGIC_BYTE_V1)
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

    /** Set up type configs without launching the poll loop. */
    private fun startWithConfigs(
        keyType: KafkaFieldType = KafkaFieldType.STRING,
        valueType: KafkaFieldType = KafkaFieldType.STRING
    ) {
        client.currentKeyConfig = fieldConfig(keyType, isKey = true)
        client.currentValueConfig = fieldConfig(valueType, isKey = false)
    }

    @Nested
    @DisplayName("Wire format detection, V0 (payload prefix)")
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
    }

    @Nested
    @DisplayName("Wire format detection, V1 (header GUID)")
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

        @Test
        fun `should return null when header value bytes are null`() {
            val headers = RecordHeaders(listOf(
                RecordHeader(SchemaId.VALUE_SCHEMA_ID_HEADER, null)
            ))
            val result = client.getSchemaGuidFromHeaders(headers, isKey = false)
            assertNull(result)
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

            // SR convention: null schemaType should expect AVRO
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
    @DisplayName("Protobuf deserialization")
    inner class ProtobufDeserialization {

        @Test
        fun `should deserialize protobuf payload to DynamicMessage`() {
            val schema = ProtobufSchema(protoSchemaText)
            // Index [0] means the first (and only) message type in the schema
            val indexBytes = MessageIndexes(listOf(0)).toByteArray()

            val descriptor = schema.toDescriptor()!!
            val protoMsg = DynamicMessage.newBuilder(descriptor)
                .setField(descriptor.findFieldByName("name"), "Alice")
                .setField(descriptor.findFieldByName("age"), 30)
                .build()

            val fullPayload = indexBytes + protoMsg.toByteArray()

            val result = client.deserializeProtobuf(fullPayload, schema)
            assertEquals("Alice", result.getField(descriptor.findFieldByName("name")))
            assertEquals(30, result.getField(descriptor.findFieldByName("age")))
        }

        @Test
        fun `should throw when message index is out of range`() {
            val schema = ProtobufSchema(protoSchemaText)
            // Index [99] doesn't exist in the schema,  only one message type defined
            val indexBytes = MessageIndexes(listOf(99)).toByteArray()
            val fullPayload = indexBytes + byteArrayOf(1, 2, 3)

            assertThrows(Exception::class.java) {
                client.deserializeProtobuf(fullPayload, schema)
            }
        }

        @Test
        fun `should throw SerializationException when no descriptor found`() {
            // Mock schema where both toDescriptor overloads return null
            val mockSchema = mock<ProtobufSchema> {
                on { toMessageName(any()) } doReturn "NonExistent"
                on { toDescriptor("NonExistent") } doReturn null
                on { toDescriptor() } doReturn null
                on { name() } doReturn "TestSchema"
            }
            // Index [0], valid varint so MessageIndexes.readFrom succeeds
            val indexBytes = MessageIndexes(listOf(0)).toByteArray()
            val fullPayload = indexBytes + byteArrayOf(1, 2, 3)

            val ex = assertThrows(SerializationException::class.java) {
                client.deserializeProtobuf(fullPayload, mockSchema)
            }
            assertTrue(ex.message!!.contains("No descriptor for TestSchema"))
        }
    }

    @Nested
    @DisplayName("Priority and fallback behavior")
    inner class PriorityAndFallback {

        @Test
        fun `should prefer V1 header GUID over V0 payload ID`(): Unit = runBlocking {
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
        fun `should throw when schema fetch fails`(): Unit = runBlocking {
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
        fun `should throw SerializationException when schema response has invalid schema`(): Unit = runBlocking {
            val schemaId = 12
            val wireBytes = buildV0Payload(schemaId, byteArrayOf(1, 2, 3))
            val headers = RecordHeaders()

            whenever(mockFetcher.getSchemaIdInfo(schemaId)).thenReturn(
                SchemaByIdResponse(schema = "not valid schema content")
            )

            assertThrows(Exception::class.java) {
                runBlocking { client.deserializeSchemaEncoded(wireBytes, mockFetcher, headers, isKey = false) }
            }
        }

        @Test
        fun `should throw SerializationException for unsupported schema type`(): Unit = runBlocking {
            val schemaId = 13
            val wireBytes = buildV0Payload(schemaId, byteArrayOf(1, 2, 3))
            val headers = RecordHeaders()

            // Pre-populate cache with a mock ParsedSchema that isn't Avro/Protobuf/Json
            client.schemaCache[schemaId.toString()] = mock<ParsedSchema> {
                on { schemaType() } doReturn "UNKNOWN"
            }

            val ex = assertThrows(SerializationException::class.java) {
                runBlocking { client.deserializeSchemaEncoded(wireBytes, mockFetcher, headers, isKey = false) }
            }
            assertTrue(ex.message!!.contains("Unsupported schema type"))
        }

        @Test
        fun `should throw when deserialization fails`(): Unit = runBlocking {
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

        @Test
        fun `should use key header for schema GUID when isKey is true`(): Unit = runBlocking {
            val guid = UUID.randomUUID()
            val avroPayload = encodeAvroPayload(avroSchemaJson, "KeyUser", 42)
            val headers = RecordHeaders(listOf(
                RecordHeader(SchemaId.KEY_SCHEMA_ID_HEADER, buildV1HeaderValue(guid))
            ))

            whenever(mockFetcher.getSchemaByGuid(guid.toString())).thenReturn(
                SchemaByIdResponse(schema = avroSchemaJson, schemaType = "AVRO")
            )

            val result = client.deserializeSchemaEncoded(avroPayload, mockFetcher, headers, isKey = true)

            assertTrue(result is GenericData.Record)
            assertEquals("KeyUser", (result as GenericData.Record).get("name").toString())
            verify(mockFetcher).getSchemaByGuid(guid.toString())
        }
    }

    @Nested
    @DisplayName("Schema caching")
    inner class SchemaCaching {

        @Test
        fun `should cache schema and not re-fetch for same schema ID`(): Unit = runBlocking {
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
    @DisplayName("extractValue")
    inner class ExtractValue {

        @Test
        fun `should return null for null element`() = runBlocking {
            startWithConfigs()
            val result = client.extractValue(null, "t", mockFetcher, RecordHeaders(), isKey = false)
            assertNull(result)
        }

        @Test
        fun `should return null for JsonNull element`() = runBlocking {
            startWithConfigs()
            val result = client.extractValue(JsonNull, "t", mockFetcher, RecordHeaders(), isKey = false)
            assertNull(result)
        }

        @Test
        fun `should deserialize __raw__ with SCHEMA_REGISTRY via schema`() = runBlocking {
            startWithConfigs(valueType = KafkaFieldType.SCHEMA_REGISTRY)

            val schemaId = 1
            val avroPayload = encodeAvroPayload(avroSchemaJson, "Eve", 28)
            val wireBytes = buildV0Payload(schemaId, avroPayload)
            val rawElement = JsonObject(mapOf("__raw__" to JsonPrimitive(Base64.getEncoder().encodeToString(wireBytes))))

            whenever(mockFetcher.getSchemaIdInfo(schemaId)).thenReturn(
                SchemaByIdResponse(schema = avroSchemaJson, schemaType = "AVRO")
            )

            val result = client.extractValue(rawElement, "t", mockFetcher, RecordHeaders(), isKey = false)
            assertTrue(result is GenericData.Record)
            assertEquals("Eve", (result as GenericData.Record).get("name").toString())
        }

        @Test
        fun `should convert __raw__ with STRING type to UTF-8 string`() = runBlocking {
            startWithConfigs(valueType = KafkaFieldType.STRING)

            val rawElement = JsonObject(mapOf("__raw__" to JsonPrimitive(Base64.getEncoder().encodeToString("hello".toByteArray()))))
            val result = client.extractValue(rawElement, "test-topic", mockFetcher, RecordHeaders(), isKey = false)
            assertEquals("hello", result)
        }

        @Test
        fun `should convert __raw__ with LONG type to Long`() = runBlocking {
            startWithConfigs(valueType = KafkaFieldType.LONG)

            val longBytes = ByteBuffer.allocate(8).putLong(42L).array()
            val rawElement = JsonObject(mapOf("__raw__" to JsonPrimitive(Base64.getEncoder().encodeToString(longBytes))))
            val result = client.extractValue(rawElement, "test-topic", mockFetcher, RecordHeaders(), isKey = false)
            assertEquals(42L, result)
        }

        @Test
        fun `should convert plain JsonPrimitive with STRING type to string content`() = runBlocking {
            startWithConfigs(valueType = KafkaFieldType.STRING)
            val result = client.extractValue(JsonPrimitive("hello"), "t", mockFetcher, RecordHeaders(), isKey = false)
            assertEquals("hello", result)
        }

        @Test
        fun `should convert plain JsonPrimitive with LONG type to parsed Long`() = runBlocking {
            startWithConfigs(valueType = KafkaFieldType.LONG)
            val result = client.extractValue(JsonPrimitive("999"), "t", mockFetcher, RecordHeaders(), isKey = false)
            assertEquals(999L, result)
        }

        @Test
        fun `should default to STRING when no config is set`() = runBlocking {
            // Do NOT call startWithConfigs, leave configs null to avoid potential reader exception
            val result = client.extractValue(JsonPrimitive("fallback"), "t", mockFetcher, RecordHeaders(), isKey = false)
            assertEquals("fallback", result)
        }

        @Test
        fun `should return empty string when __raw__ decodes to empty bytes`() = runBlocking {
            startWithConfigs(valueType = KafkaFieldType.STRING)
            val rawElement = JsonObject(mapOf("__raw__" to JsonPrimitive(Base64.getEncoder().encodeToString(byteArrayOf()))))
            val result = client.extractValue(rawElement, "test-topic", mockFetcher, RecordHeaders(), isKey = false)
            assertEquals("", result)
        }

        @Test
        fun `should use key config type when isKey is true`() = runBlocking {
            startWithConfigs(keyType = KafkaFieldType.LONG, valueType = KafkaFieldType.STRING)

            val longBytes = ByteBuffer.allocate(8).putLong(99L).array()
            val rawElement = JsonObject(mapOf("__raw__" to JsonPrimitive(Base64.getEncoder().encodeToString(longBytes))))
            val result = client.extractValue(rawElement, "test-topic", mockFetcher, RecordHeaders(), isKey = true)
            assertEquals(99L, result)
        }

        @Test
        fun `should return null when __raw__ value is JsonNull`() = runBlocking {
            startWithConfigs()
            val rawElement = JsonObject(mapOf("__raw__" to JsonNull))
            val result = client.extractValue(rawElement, "t", mockFetcher, RecordHeaders(), isKey = false)
            assertNull(result)
        }
    }

    @Nested
    @DisplayName("estimateJsonSize")
    inner class EstimateJsonSize {

        @Test
        fun `should return 0 for null`() {
            assertEquals(0, client.estimateJsonSize(null))
        }

        @Test
        fun `should return 0 for JsonNull`() {
            assertEquals(0, client.estimateJsonSize(JsonNull))
        }

        @Test
        fun `should decode base64 in __raw__ and return byte count`() {
            val original = "hello world".toByteArray()
            val encoded = Base64.getEncoder().encodeToString(original)
            val element = JsonObject(mapOf("__raw__" to JsonPrimitive(encoded)))
            assertEquals(original.size, client.estimateJsonSize(element))
        }

        @Test
        fun `should fall back to string byte count for invalid base64 in __raw__`() {
            val invalidBase64 = "not-valid-base64!!!"
            val element = JsonObject(mapOf("__raw__" to JsonPrimitive(invalidBase64)))
            assertEquals(invalidBase64.toByteArray().size, client.estimateJsonSize(element))
        }

        @Test
        fun `should return UTF-8 byte count for JsonPrimitive`() {
            val text = "hello"
            assertEquals(text.toByteArray().size, client.estimateJsonSize(JsonPrimitive(text)))
        }

        @Test
        fun `should return toString byte count for JsonObject without __raw__`() {
            val obj = JsonObject(mapOf("key" to JsonPrimitive("val")))
            assertEquals(obj.toString().toByteArray().size, client.estimateJsonSize(obj))
        }

        @Test
        fun `should return 0 when __raw__ value is JsonNull`() {
            val element = JsonObject(mapOf("__raw__" to JsonNull))
            assertEquals(0, client.estimateJsonSize(element))
        }
    }

    @Nested
    @DisplayName("convertJsonToType")
    inner class ConvertJsonToType {

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
        fun `should throw for JsonObject with numeric type`() {
            val obj = JsonObject(mapOf("a" to JsonPrimitive(1)))
            assertThrows(SerializationException::class.java) {
                client.convertJsonToType(obj, KafkaFieldType.LONG)
            }
        }

        @Test
        fun `should return toString for JsonObject with STRING type`() {
            val obj = JsonObject(mapOf("a" to JsonPrimitive(1)))
            val result = client.convertJsonToType(obj, KafkaFieldType.STRING)
            assertEquals(obj.toString(), result)
        }

        @Test
        fun `should throw for non-numeric DOUBLE`() {
            assertThrows(SerializationException::class.java) {
                client.convertJsonToType(JsonPrimitive("not-a-double"), KafkaFieldType.DOUBLE)
            }
        }

        @Test
        fun `should throw for non-numeric FLOAT`() {
            assertThrows(SerializationException::class.java) {
                client.convertJsonToType(JsonPrimitive("not-a-float"), KafkaFieldType.FLOAT)
            }
        }

        @Test
        fun `should throw for SCHEMA_REGISTRY type`() {
            assertThrows(IllegalArgumentException::class.java) {
                client.convertJsonToType(JsonPrimitive("value"), KafkaFieldType.SCHEMA_REGISTRY)
            }
        }
    }

    @Nested
    @DisplayName("convertBytesToType")
    inner class ConvertBytesToType {

        @Test
        fun `should return UTF-8 string for STRING type`() {
            val result = client.convertBytesToType("hello".toByteArray(), "test-topic", KafkaFieldType.STRING)
            assertEquals("hello", result)
        }

        @Test
        fun `should read 8-byte big-endian LONG`() {
            val bytes = ByteBuffer.allocate(8).putLong(123456789L).array()
            val result = client.convertBytesToType(bytes, "test-topic", KafkaFieldType.LONG)
            assertEquals(123456789L, result)
        }

        @Test
        fun `should throw SerializationException for non-8-byte LONG`() {
            val bytes = "42".toByteArray()
            assertThrows(SerializationException::class.java) {
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
    }

    @Nested
    @DisplayName("Deserializer factory")
    inner class DeserializerFactory {

        @Test
        fun `createDeserializerOrNull should return null for SCHEMA_REGISTRY`() {
            assertNull(client.createDeserializerOrNull(KafkaFieldType.SCHEMA_REGISTRY))
        }

        @Test
        fun `createDeserializerOrNull should return null for PROTOBUF_CUSTOM`() {
            assertNull(client.createDeserializerOrNull(KafkaFieldType.PROTOBUF_CUSTOM))
        }

        @Test
        fun `createDeserializerOrNull should return null for AVRO_CUSTOM`() {
            assertNull(client.createDeserializerOrNull(KafkaFieldType.AVRO_CUSTOM))
        }

        @Test
        fun `createDeserializerOrNull should return StringDeserializer for STRING`() {
            assertTrue(client.createDeserializerOrNull(KafkaFieldType.STRING) is StringDeserializer)
        }

        @Test
        fun `createDeserializerOrNull should return LongDeserializer for LONG`() {
            assertTrue(client.createDeserializerOrNull(KafkaFieldType.LONG) is LongDeserializer)
        }

        @Test
        fun `createDeserializer should return StringDeserializer for JSON`() {
            assertTrue(client.createDeserializer(KafkaFieldType.JSON) is StringDeserializer)
        }

        @Test
        fun `createDeserializer should return IntegerDeserializer for INTEGER`() {
            assertTrue(client.createDeserializer(KafkaFieldType.INTEGER) is IntegerDeserializer)
        }

        @Test
        fun `createDeserializer should return DoubleDeserializer for DOUBLE`() {
            assertTrue(client.createDeserializer(KafkaFieldType.DOUBLE) is DoubleDeserializer)
        }

        @Test
        fun `createDeserializer should return FloatDeserializer for FLOAT`() {
            assertTrue(client.createDeserializer(KafkaFieldType.FLOAT) is FloatDeserializer)
        }

        @Test
        fun `createDeserializer should return ByteArrayDeserializer for BASE64`() {
            assertTrue(client.createDeserializer(KafkaFieldType.BASE64) is ByteArrayDeserializer)
        }

        @Test
        fun `createDeserializer should return VoidDeserializer for NULL`() {
            assertTrue(client.createDeserializer(KafkaFieldType.NULL) is VoidDeserializer)
        }

        @Test
        fun `createDeserializer should throw for SCHEMA_REGISTRY`() {
            assertThrows(IllegalArgumentException::class.java) {
                client.createDeserializer(KafkaFieldType.SCHEMA_REGISTRY)
            }
        }

        @Test
        fun `createDeserializer should throw for PROTOBUF_CUSTOM`() {
            assertThrows(IllegalArgumentException::class.java) {
                client.createDeserializer(KafkaFieldType.PROTOBUF_CUSTOM)
            }
        }

        @Test
        fun `createDeserializer should throw for AVRO_CUSTOM`() {
            assertThrows(IllegalArgumentException::class.java) {
                client.createDeserializer(KafkaFieldType.AVRO_CUSTOM)
            }
        }
    }

    @Nested
    @DisplayName("buildSubsequentConsumeRequest")
    inner class BuildSubsequentConsumeRequest {

        @Test
        fun `should return fromBeginning=false when nextOffsets is empty`() {
            val request = client.buildSubsequentConsumeRequest()
            assertEquals(false, request.fromBeginning)
            assertNull(request.offsets)
        }

        @Test
        fun `should include partition offsets when nextOffsets is populated`() {
            client.nextOffsets[0] = 100L
            client.nextOffsets[1] = 200L
            val request = client.buildSubsequentConsumeRequest()
            assertNull(request.fromBeginning)
            assertNotNull(request.offsets)
            assertEquals(2, request.offsets!!.size)
            val offsetMap = request.offsets.associate { it.partitionId to it.offset }
            assertEquals(100L, offsetMap[0])
            assertEquals(200L, offsetMap[1])
        }
    }

    // ── Utilities ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("isRetryableStatus")
    inner class IsRetryableStatus {

        @Test
        fun `401 should be retryable`() {
            assertTrue(client.isRetryableStatus(401))
        }

        @Test
        fun `429 should be retryable`() {
            assertTrue(client.isRetryableStatus(429))
        }

        @Test
        fun `500 should be retryable`() {
            assertTrue(client.isRetryableStatus(500))
        }

        @Test
        fun `503 should be retryable`() {
            assertTrue(client.isRetryableStatus(503))
        }

        @Test
        fun `400 should not be retryable`() {
            assertFalse(client.isRetryableStatus(400))
        }

        @Test
        fun `404 should not be retryable`() {
            assertFalse(client.isRetryableStatus(404))
        }
    }

    @Nested
    @DisplayName("getRecordSize")
    inner class GetRecordSize {

        private fun buildRecord(keySize: Int, valueSize: Int, key: Any? = "k", value: Any? = "v"): ConsumerRecord<Any, Any> {
            return ConsumerRecord(
                "topic", 0, 0L, 0L, TimestampType.CREATE_TIME,
                keySize, valueSize, key, value,
                RecordHeaders(), null
            )
        }

        @Test
        fun `should return sum of positive serialized sizes`() {
            val record = buildRecord(keySize = 10, valueSize = 20)
            assertEquals(30L, client.getRecordSize(record))
        }

        @Test
        fun `should estimate from toString when serialized sizes are negative`() {
            val record = buildRecord(keySize = -1, valueSize = -1, key = "abc", value = "defgh")
            // "abc" = 3 bytes, "defgh" = 5 bytes
            assertEquals(8L, client.getRecordSize(record))
        }

        @Test
        fun `should handle null key and value with negative sizes`() {
            val record = buildRecord(keySize = -1, valueSize = -1, key = null, value = null)
            assertEquals(0L, client.getRecordSize(record))
        }

        @Test
        fun `should use serialized key size when positive and estimate value when negative`() {
            val record = buildRecord(keySize = 15, valueSize = -1, key = "k", value = "hello")
            // keySize=15 (from serialized), valueSize=5 (estimated from "hello")
            assertEquals(20L, client.getRecordSize(record))
        }
    }
    }
}
