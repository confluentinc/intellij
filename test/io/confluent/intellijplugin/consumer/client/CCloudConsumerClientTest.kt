package io.confluent.intellijplugin.consumer.client

import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.ccloud.cache.DataPlaneCache
import io.confluent.intellijplugin.ccloud.fetcher.DataPlaneFetcher
import io.confluent.intellijplugin.ccloud.fetcher.DataPlaneFetcherImpl
import io.confluent.intellijplugin.ccloud.model.response.ConsumeRecordsRequest
import io.confluent.intellijplugin.ccloud.model.response.ConsumeRecordsResponse
import io.confluent.intellijplugin.ccloud.model.response.PartitionConsumeData
import io.confluent.intellijplugin.ccloud.model.response.PartitionConsumeRecord
import io.confluent.intellijplugin.ccloud.model.response.PartitionConsumeRecordHeader
import io.confluent.intellijplugin.ccloud.model.response.SchemaByIdResponse
import io.confluent.intellijplugin.ccloud.model.response.SinglePartitionConsumeResponse
import io.confluent.intellijplugin.ccloud.model.response.TimestampType as ApiTimestampType
import io.confluent.intellijplugin.common.models.KafkaFieldType
import io.confluent.intellijplugin.common.settings.StorageConsumerConfig
import io.confluent.intellijplugin.consumer.models.ConsumerProducerFieldConfig
import io.confluent.intellijplugin.consumer.models.ConsumerStartType
import io.confluent.intellijplugin.data.CCloudClusterDataManager
import io.confluent.intellijplugin.ccloud.model.response.PartitionData
import io.confluent.intellijplugin.registry.KafkaRegistryFormat
import io.confluent.intellijplugin.registry.KafkaRegistryType
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.protobuf.MessageIndexes
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema
import com.google.protobuf.DynamicMessage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.io.EncoderFactory
import io.confluent.kafka.serializers.schema.id.SchemaId
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.record.TimestampType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.nio.ByteBuffer
import java.util.Base64
import java.util.UUID

@TestApplication
class CCloudConsumerClientTest {

    private lateinit var mockDataManager: CCloudClusterDataManager
    private lateinit var mockFetcher: DataPlaneFetcher
    private lateinit var client: CCloudConsumerClient

    companion object {
        private const val TEST_SR_CLUSTER_ID = "lsrc-test123"
    }

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
        mockFetcher = mock()
        val mockFetcherImpl = mock<DataPlaneFetcherImpl>()
        val mockDataPlaneCache = mock<DataPlaneCache> {
            on { getSchemaRegistryId() } doReturn TEST_SR_CLUSTER_ID
            on { getFetcher() } doReturn mockFetcherImpl
        }
        mockDataManager = mock {
            on { getDataPlaneCache() } doReturn mockDataPlaneCache
        }
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
            val registryId = SchemaRegistryClusterId(TEST_SR_CLUSTER_ID)
            client.schemaCache
                .getOrPut(registryId) { ConcurrentHashMap() }[SchemaCacheKey.ById(schemaId)] = mock<ParsedSchema> {
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

        @Test
        fun `should isolate schemas across different registry cluster IDs`() {
            val schemaId = 42
            val registryA = SchemaRegistryClusterId("lsrc-aaa")
            val registryB = SchemaRegistryClusterId("lsrc-bbb")
            val key = SchemaCacheKey.ById(schemaId)

            val schemaA = mock<ParsedSchema> { on { schemaType() } doReturn "AVRO" }
            val schemaB = mock<ParsedSchema> { on { schemaType() } doReturn "PROTOBUF" }

            client.schemaCache.getOrPut(registryA) { ConcurrentHashMap() }[key] = schemaA
            client.schemaCache.getOrPut(registryB) { ConcurrentHashMap() }[key] = schemaB

            assertSame(schemaA, client.schemaCache[registryA]!![key])
            assertSame(schemaB, client.schemaCache[registryB]!![key])
        }
    }

    @Nested
    @DisplayName("extractValue")
    inner class ExtractValue {

        @Test
        fun `should return null for null bytes`() = runBlocking {
            startWithConfigs()
            val result = client.extractValue(null, "t", mockFetcher, RecordHeaders(), isKey = false)
            assertNull(result)
        }

        @Test
        fun `should deserialize with SCHEMA_REGISTRY via schema`() = runBlocking {
            startWithConfigs(valueType = KafkaFieldType.SCHEMA_REGISTRY)

            val schemaId = 1
            val avroPayload = encodeAvroPayload(avroSchemaJson, "Eve", 28)
            val wireBytes = buildV0Payload(schemaId, avroPayload)

            whenever(mockFetcher.getSchemaIdInfo(schemaId)).thenReturn(
                SchemaByIdResponse(schema = avroSchemaJson, schemaType = "AVRO")
            )

            val result = client.extractValue(wireBytes, "t", mockFetcher, RecordHeaders(), isKey = false)
            assertTrue(result is GenericData.Record)
            assertEquals("Eve", (result as GenericData.Record).get("name").toString())
        }

        @Test
        fun `should default to STRING when no config is set`() = runBlocking {
            // Do NOT call startWithConfigs, leave configs null to avoid potential reader exception
            val result = client.extractValue("fallback".toByteArray(), "t", mockFetcher, RecordHeaders(), isKey = false)
            assertEquals("fallback", result)
        }

        @Test
        fun `should return empty string when bytes are empty`() = runBlocking {
            startWithConfigs(valueType = KafkaFieldType.STRING)
            val result = client.extractValue(byteArrayOf(), "test-topic", mockFetcher, RecordHeaders(), isKey = false)
            assertEquals("", result)
        }

        @Test
        fun `should use key config type when isKey is true`() = runBlocking {
            startWithConfigs(keyType = KafkaFieldType.LONG, valueType = KafkaFieldType.STRING)
            val longBytes = ByteBuffer.allocate(8).putLong(99L).array()
            val result = client.extractValue(longBytes, "test-topic", mockFetcher, RecordHeaders(), isKey = true)
            assertEquals(99L, result)
        }

        @Test
        fun `should deserialize all primitive field types from raw bytes`() = runBlocking {
            data class Case(val type: KafkaFieldType, val bytes: ByteArray, val expected: Any?)

            val cases = listOf(
                Case(KafkaFieldType.STRING, "hello".toByteArray(), "hello"),
                Case(KafkaFieldType.JSON, """{"a":1}""".toByteArray(), """{"a":1}"""),
                Case(KafkaFieldType.LONG, ByteBuffer.allocate(8).putLong(42L).array(), 42L),
                Case(KafkaFieldType.INTEGER, ByteBuffer.allocate(4).putInt(7).array(), 7),
                Case(KafkaFieldType.DOUBLE, ByteBuffer.allocate(8).putDouble(3.14).array(), 3.14),
                Case(KafkaFieldType.FLOAT, ByteBuffer.allocate(4).putFloat(2.5f).array(), 2.5f),
                Case(KafkaFieldType.BASE64, byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3)),
            )

            for (case in cases) {
                startWithConfigs(valueType = case.type)
                val result = client.extractValue(case.bytes, "t", mockFetcher, RecordHeaders(), isKey = false)
                if (case.expected is ByteArray) {
                    assertArrayEquals(case.expected, result as ByteArray, "Failed for ${case.type}")
                } else {
                    assertEquals(case.expected, result, "Failed for ${case.type}")
                }
            }
        }

        @Test
        fun `should throw for NULL type with non-null bytes`() {
            startWithConfigs(valueType = KafkaFieldType.NULL)
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    client.extractValue(byteArrayOf(1, 2), "t", mockFetcher, RecordHeaders(), isKey = false)
                }
            }
        }

        @Test
        fun `should deserialize custom protobuf schema types`() {
            val protoSchema = ProtobufSchema(
                "syntax = \"proto3\"; message TestMessage { string name = 1; }"
            )
            val message = DynamicMessage.newBuilder(protoSchema.toDescriptor())
                .setField(protoSchema.toDescriptor().findFieldByName("name"), "hello")
                .build()
            val bytes = message.toByteArray()

            client.currentValueConfig = ConsumerProducerFieldConfig(
                type = KafkaFieldType.PROTOBUF_CUSTOM,
                valueText = "",
                isKey = false,
                topic = "test-topic",
                registryType = KafkaRegistryType.NONE,
                schemaName = "",
                schemaFormat = KafkaRegistryFormat.PROTOBUF,
                parsedSchema = protoSchema
            )

            val result = runBlocking {
                client.extractValue(bytes, "test-topic", mockFetcher, RecordHeaders(), isKey = false)
            }
            assertNotNull(result)
            assertTrue(result is DynamicMessage)
            assertEquals("hello", (result as DynamicMessage).getField(protoSchema.toDescriptor().findFieldByName("name")))
        }

        @Test
        fun `should deserialize custom avro schema types`() {
            val avroSchema = Schema.Parser().parse(
                """{"type":"record","name":"Test","fields":[{"name":"name","type":"string"}]}"""
            )
            val record = GenericData.Record(avroSchema).apply { put("name", "hello") }
            val baos = ByteArrayOutputStream()
            val writer = GenericDatumWriter<GenericData.Record>(avroSchema)
            val encoder = EncoderFactory.get().binaryEncoder(baos, null)
            writer.write(record, encoder)
            encoder.flush()
            val bytes = baos.toByteArray()

            client.currentValueConfig = ConsumerProducerFieldConfig(
                type = KafkaFieldType.AVRO_CUSTOM,
                valueText = "",
                isKey = false,
                topic = "test-topic",
                registryType = KafkaRegistryType.NONE,
                schemaName = "",
                schemaFormat = KafkaRegistryFormat.AVRO,
                parsedSchema = AvroSchema(avroSchema)
            )

            val result = runBlocking {
                client.extractValue(bytes, "test-topic", mockFetcher, RecordHeaders(), isKey = false)
            }
            assertNotNull(result)
            assertTrue(result is GenericData.Record)
            assertEquals("hello", (result as GenericData.Record).get("name").toString())
        }

        @Test
        fun `should deserialize custom protobuf schema types for key`() {
            val protoSchema = ProtobufSchema(
                "syntax = \"proto3\"; message TestKey { string id = 1; }"
            )
            val message = DynamicMessage.newBuilder(protoSchema.toDescriptor())
                .setField(protoSchema.toDescriptor().findFieldByName("id"), "key-123")
                .build()
            val bytes = message.toByteArray()

            client.currentKeyConfig = ConsumerProducerFieldConfig(
                type = KafkaFieldType.PROTOBUF_CUSTOM,
                valueText = "",
                isKey = true,
                topic = "test-topic",
                registryType = KafkaRegistryType.NONE,
                schemaName = "",
                schemaFormat = KafkaRegistryFormat.PROTOBUF,
                parsedSchema = protoSchema
            )

            val result = runBlocking {
                client.extractValue(bytes, "test-topic", mockFetcher, RecordHeaders(), isKey = true)
            }
            assertNotNull(result)
            assertTrue(result is DynamicMessage)
            assertEquals("key-123", (result as DynamicMessage).getField(protoSchema.toDescriptor().findFieldByName("id")))
        }
    }

    @Nested
    @DisplayName("createDeserializerOrNull")
    inner class CreateDeserializerOrNull {

        @Test
        fun `should return null for schema registry type`() {
            assertNull(client.createDeserializerOrNull(KafkaFieldType.SCHEMA_REGISTRY), "Expected null for SCHEMA_REGISTRY")
        }

        @Test
        fun `should return null for custom types without config`() {
            for (type in listOf(KafkaFieldType.PROTOBUF_CUSTOM, KafkaFieldType.AVRO_CUSTOM)) {
                assertNull(client.createDeserializerOrNull(type), "Expected null for $type without config")
            }
        }

        @Test
        fun `should return deserializer for custom types with config`() {
            val protoConfig = ConsumerProducerFieldConfig(
                type = KafkaFieldType.PROTOBUF_CUSTOM, valueText = "", isKey = false, topic = "t",
                registryType = KafkaRegistryType.NONE, schemaName = "", schemaFormat = KafkaRegistryFormat.PROTOBUF,
                parsedSchema = ProtobufSchema("syntax = \"proto3\"; message T { string f = 1; }")
            )
            assertNotNull(client.createDeserializerOrNull(KafkaFieldType.PROTOBUF_CUSTOM, protoConfig))

            val avroConfig = ConsumerProducerFieldConfig(
                type = KafkaFieldType.AVRO_CUSTOM, valueText = "", isKey = false, topic = "t",
                registryType = KafkaRegistryType.NONE, schemaName = "", schemaFormat = KafkaRegistryFormat.AVRO,
                parsedSchema = AvroSchema(Schema.Parser().parse("""{"type":"record","name":"T","fields":[{"name":"f","type":"string"}]}"""))
            )
            assertNotNull(client.createDeserializerOrNull(KafkaFieldType.AVRO_CUSTOM, avroConfig))
        }

        @Test
        fun `should return a deserializer for primitive types`() {
            for (type in listOf(KafkaFieldType.STRING, KafkaFieldType.LONG, KafkaFieldType.INTEGER,
                KafkaFieldType.DOUBLE, KafkaFieldType.FLOAT, KafkaFieldType.BASE64, KafkaFieldType.JSON, KafkaFieldType.NULL)) {
                assertNotNull(client.createDeserializerOrNull(type), "Expected non-null for $type")
            }
        }
    }

    @Nested
    @DisplayName("decodeRawBytes")
    inner class DecodeRawBytes {

        @Test
        fun `should return null for null element`() {
            assertNull(client.decodeRawBytes(null))
        }

        @Test
        fun `should return null for JsonNull element`() {
            assertNull(client.decodeRawBytes(JsonNull))
        }

        @Test
        fun `should decode base64 from __raw__ element`() {
            val original = "hello world".toByteArray()
            val encoded = Base64.getEncoder().encodeToString(original)
            val element = JsonObject(mapOf("__raw__" to JsonPrimitive(encoded)))
            assertArrayEquals(original, client.decodeRawBytes(element))
        }

        @Test
        fun `should return null for JsonObject without __raw__`() {
            val element = JsonObject(mapOf("key" to JsonPrimitive("val")))
            assertNull(client.decodeRawBytes(element))
        }

        @Test
        fun `should return empty array for empty base64`() {
            val element = JsonObject(mapOf("__raw__" to JsonPrimitive("")))
            assertArrayEquals(byteArrayOf(), client.decodeRawBytes(element))
        }

        @Test
        fun `should return null for plain JsonPrimitive`() {
            assertNull(client.decodeRawBytes(JsonPrimitive("hello")))
        }

        @Test
        fun `should return null when __raw__ value is JsonNull`() {
            assertNull(client.decodeRawBytes(JsonObject(mapOf("__raw__" to JsonNull))))
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

    @Nested
    @DisplayName("start")
    inner class Start {

        @AfterEach
        fun tearDown() {
            client.stop()
        }

        private fun startWith(properties: Map<String, String> = emptyMap()) {
            val config = StorageConsumerConfig(properties = properties)
            client.start(config, fieldConfig(KafkaFieldType.STRING), fieldConfig(KafkaFieldType.STRING, isKey = true),
                consume = { _, _ -> }, timestampUpdate = {}, consumeError = { _, _, _ -> })
        }

        @Test
        fun `should resolve config properties and pass them to ConsumeRecordsRequest`() {
            startWith(mapOf(
                ConsumerConfig.MAX_POLL_RECORDS_CONFIG to "10",
                ConsumerConfig.FETCH_MAX_BYTES_CONFIG to "2048"
            ))

            val request = client.buildSubsequentConsumeRequest()
            assertEquals(10, request.maxPollRecords)
            assertEquals(2048, request.fetchMaxBytes)
        }

        @Test
        fun `should use server defaults when properties are absent from config`() {
            startWith()

            val request = client.buildSubsequentConsumeRequest()
            assertNull(request.maxPollRecords, "Null lets server use its default (500)")
            assertNull(request.fetchMaxBytes, "Null lets server use its default (50MB)")
        }

        @Test
        fun `should use server defaults when property values are not valid integers`() {
            startWith(mapOf(
                ConsumerConfig.MAX_POLL_RECORDS_CONFIG to "not-a-number",
                ConsumerConfig.FETCH_MAX_BYTES_CONFIG to ""
            ))

            val request = client.buildSubsequentConsumeRequest()
            assertNull(request.maxPollRecords)
            assertNull(request.fetchMaxBytes)
        }
    }

    @Nested
    @DisplayName("stop")
    inner class Stop {

        @Test
        fun `should clear resolved advanced settings`() {
            client.resolvedMaxPollRecords = 10
            client.resolvedFetchMaxBytes = 2048

            client.stop()

            assertNull(client.resolvedMaxPollRecords)
            assertNull(client.resolvedFetchMaxBytes)
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

    // ── Partition filtering & offset tracking ─────────────────────────────

    private fun buildResponse(
        vararg partitions: Pair<Int, Long>,
        records: List<PartitionConsumeRecord> = emptyList()
    ): ConsumeRecordsResponse {
        return ConsumeRecordsResponse(
            clusterId = "lkc-test",
            topicName = "test-topic",
            partitionDataList = partitions.map { (pid, nextOffset) ->
                PartitionConsumeData(
                    partitionId = pid,
                    nextOffset = nextOffset,
                    records = records.filter { it.partitionId == pid }
                )
            }
        )
    }

    private fun buildConfig(
        topic: String = "test-topic",
        startType: ConsumerStartType = ConsumerStartType.NOW,
        offset: Long? = null,
        partitions: String? = null,
        properties: Map<String, String> = emptyMap()
    ): StorageConsumerConfig {
        val startWithMap = mutableMapOf("type" to startType.name)
        offset?.let { startWithMap["offset"] = it.toString() }
        return StorageConsumerConfig(
            topic = topic,
            partitions = partitions,
            startWith = startWithMap,
            properties = properties
        )
    }

    @Nested
    @DisplayName("updateNextOffsets")
    inner class UpdateNextOffsets {

        @Test
        fun `should track all partition offsets when filter is null`() {
            val response = buildResponse(0 to 100L, 1 to 200L, 2 to 300L)

            client.updateNextOffsets(response, partitionFilter = null)

            assertEquals(3, client.nextOffsets.size)
            assertEquals(100L, client.nextOffsets[0])
            assertEquals(200L, client.nextOffsets[1])
            assertEquals(300L, client.nextOffsets[2])
        }

        @Test
        fun `should only track filtered partitions when filter is set`() {
            val response = buildResponse(0 to 100L, 1 to 200L, 2 to 300L)

            client.updateNextOffsets(response, partitionFilter = setOf(0, 2))

            assertEquals(2, client.nextOffsets.size)
            assertEquals(100L, client.nextOffsets[0])
            assertEquals(300L, client.nextOffsets[2])
            assertFalse(client.nextOffsets.containsKey(1))
        }

        @Test
        fun `should remove stale partitions not in response`() {
            client.nextOffsets[0] = 50L
            client.nextOffsets[1] = 60L
            client.nextOffsets[2] = 70L

            val response = buildResponse(0 to 100L, 2 to 300L)
            client.updateNextOffsets(response, partitionFilter = null)

            assertEquals(2, client.nextOffsets.size)
            assertEquals(100L, client.nextOffsets[0])
            assertEquals(300L, client.nextOffsets[2])
            assertFalse(client.nextOffsets.containsKey(1))
        }

        @Test
        fun `should update existing offset to new value`() {
            client.nextOffsets[0] = 50L

            val response = buildResponse(0 to 150L)
            client.updateNextOffsets(response, partitionFilter = null)

            assertEquals(150L, client.nextOffsets[0])
        }

        @Test
        fun `should handle empty response partition list`() {
            client.nextOffsets[0] = 50L
            client.nextOffsets[1] = 60L

            val response = ConsumeRecordsResponse(
                clusterId = "lkc-test",
                topicName = "test-topic",
                partitionDataList = emptyList()
            )
            client.updateNextOffsets(response, partitionFilter = null)

            assertTrue(client.nextOffsets.isEmpty())
        }

        @Test
        fun `should not update unfiltered partitions that are in response`() {
            client.nextOffsets[0] = 50L
            client.nextOffsets[1] = 60L

            val response = buildResponse(0 to 100L, 1 to 200L, 2 to 300L)
            client.updateNextOffsets(response, partitionFilter = setOf(0, 2))

            // Partition 1 is retained by retainAll (present in response) but not updated (not in filter)
            assertTrue(client.nextOffsets.containsKey(1))
            assertEquals(60L, client.nextOffsets[1])
            assertEquals(100L, client.nextOffsets[0])
            assertEquals(300L, client.nextOffsets[2])
        }
    }

    @Nested
    @DisplayName("validatePartitionFilter")
    inner class ValidatePartitionFilter {

        @Test
        fun `should return null when partitionsText is null`() = runBlocking {
            val result = client.validatePartitionFilter(null, "test-topic", mockFetcher)
            assertNull(result)
        }

        @Test
        fun `should return null when partitionsText is empty`() = runBlocking {
            val result = client.validatePartitionFilter("", "test-topic", mockFetcher)
            assertNull(result)
        }

        @Test
        fun `should return valid partition IDs intersected with actual partitions`() = runBlocking {
            whenever(mockFetcher.describeTopicPartitions("test-topic")).thenReturn(
                listOf(
                    PartitionData(partitionId = 0),
                    PartitionData(partitionId = 1),
                    PartitionData(partitionId = 2),
                    PartitionData(partitionId = 3)
                )
            )

            val result = client.validatePartitionFilter("0, 2", "test-topic", mockFetcher)
            assertEquals(setOf(0, 2), result)
        }

        @Test
        fun `should ignore invalid partition IDs not in topic`() = runBlocking {
            whenever(mockFetcher.describeTopicPartitions("test-topic")).thenReturn(
                listOf(PartitionData(partitionId = 0), PartitionData(partitionId = 1))
            )

            val result = client.validatePartitionFilter("0, 1, 999", "test-topic", mockFetcher)
            assertEquals(setOf(0, 1), result)
        }

        @Test
        fun `should throw when no valid partitions match actual partitions`() {
            runBlocking {
                whenever(mockFetcher.describeTopicPartitions("test-topic")).thenReturn(
                    listOf(PartitionData(partitionId = 0), PartitionData(partitionId = 1))
                )
            }

            val ex = assertThrows(IllegalStateException::class.java) {
                runBlocking { client.validatePartitionFilter("999", "test-topic", mockFetcher) }
            }
            assertTrue(ex.message!!.contains("test-topic"))
        }

    }

    @Nested
    @DisplayName("fetchInitialRecords")
    inner class FetchInitialRecords {

        private val emptyResponse = ConsumeRecordsResponse(
            clusterId = "lkc-test",
            topicName = "test-topic",
            partitionDataList = emptyList()
        )

        @Test
        fun `should resolve per-partition offsets for THE_BEGINNING when filtered`() = runBlocking {
            val config = buildConfig(startType = ConsumerStartType.THE_BEGINNING)
            val filter = setOf(0, 2)

            whenever(mockFetcher.getPartitionOffset("test-topic", 0, true)).thenReturn(0L)
            whenever(mockFetcher.getPartitionOffset("test-topic", 2, true)).thenReturn(0L)
            whenever(mockFetcher.consumeRecords(eq("test-topic"), any())).thenReturn(emptyResponse)

            client.fetchInitialRecords(config, mockFetcher, "test-topic", partitionFilter = filter)

            verify(mockFetcher).getPartitionOffset("test-topic", 0, true)
            verify(mockFetcher).getPartitionOffset("test-topic", 2, true)
            verify(mockFetcher, never()).getTopicBeginningOffsets(any())

            val captor = argumentCaptor<ConsumeRecordsRequest>()
            verify(mockFetcher).consumeRecords(eq("test-topic"), captor.capture())
            assertNotNull(captor.firstValue.offsets)
            val offsetMap = captor.firstValue.offsets!!.associate { it.partitionId to it.offset }
            assertEquals(0L, offsetMap[0])
            assertEquals(0L, offsetMap[2])
        }

        @Test
        fun `should resolve per-partition end offsets for NOW when filtered`() = runBlocking {
            val config = buildConfig(startType = ConsumerStartType.NOW)
            val filter = setOf(1)

            whenever(mockFetcher.getPartitionOffset("test-topic", 1, false)).thenReturn(500L)
            whenever(mockFetcher.consumeRecords(eq("test-topic"), any())).thenReturn(emptyResponse)

            client.fetchInitialRecords(config, mockFetcher, "test-topic", partitionFilter = filter)

            verify(mockFetcher).getPartitionOffset("test-topic", 1, false)
            verify(mockFetcher, never()).getTopicEndOffsets(any())
            Unit
        }

        @Test
        fun `should add user offset to beginning offsets for OFFSET when filtered`() = runBlocking {
            val config = buildConfig(startType = ConsumerStartType.OFFSET, offset = 10L)
            val filter = setOf(0, 1)

            whenever(mockFetcher.getPartitionOffset("test-topic", 0, true)).thenReturn(5L)
            whenever(mockFetcher.getPartitionOffset("test-topic", 1, true)).thenReturn(20L)
            whenever(mockFetcher.consumeRecords(eq("test-topic"), any())).thenReturn(emptyResponse)

            client.fetchInitialRecords(config, mockFetcher, "test-topic", partitionFilter = filter)

            val captor = argumentCaptor<ConsumeRecordsRequest>()
            verify(mockFetcher).consumeRecords(eq("test-topic"), captor.capture())
            val offsetMap = captor.firstValue.offsets!!.associate { it.partitionId to it.offset }
            assertEquals(15L, offsetMap[0])  // 5 + 10
            assertEquals(30L, offsetMap[1])  // 20 + 10
        }

        @Test
        fun `should subtract from end offsets for LATEST_OFFSET_MINUS_X when filtered`() = runBlocking {
            val config = buildConfig(startType = ConsumerStartType.LATEST_OFFSET_MINUS_X, offset = -50L)
            val filter = setOf(0)

            whenever(mockFetcher.getPartitionOffset("test-topic", 0, false)).thenReturn(100L)
            whenever(mockFetcher.consumeRecords(eq("test-topic"), any())).thenReturn(emptyResponse)

            client.fetchInitialRecords(config, mockFetcher, "test-topic", partitionFilter = filter)

            val captor = argumentCaptor<ConsumeRecordsRequest>()
            verify(mockFetcher).consumeRecords(eq("test-topic"), captor.capture())
            val offsetMap = captor.firstValue.offsets!!.associate { it.partitionId to it.offset }
            assertEquals(50L, offsetMap[0])  // max(0, 100 + (-50))
        }

        @Test
        fun `should clamp to zero for LATEST_OFFSET_MINUS_X when offset exceeds end`() = runBlocking {
            val config = buildConfig(startType = ConsumerStartType.LATEST_OFFSET_MINUS_X, offset = -200L)
            val filter = setOf(0)

            whenever(mockFetcher.getPartitionOffset("test-topic", 0, false)).thenReturn(100L)
            whenever(mockFetcher.consumeRecords(eq("test-topic"), any())).thenReturn(emptyResponse)

            client.fetchInitialRecords(config, mockFetcher, "test-topic", partitionFilter = filter)

            val captor = argumentCaptor<ConsumeRecordsRequest>()
            verify(mockFetcher).consumeRecords(eq("test-topic"), captor.capture())
            val offsetMap = captor.firstValue.offsets!!.associate { it.partitionId to it.offset }
            assertEquals(0L, offsetMap[0])  // max(0, 100 + (-200))
        }

        @Test
        fun `should use single-partition GET for timestamp start types when filtered`() = runBlocking {
            val config = buildConfig(startType = ConsumerStartType.LAST_HOUR)
            val filter = setOf(0, 3)

            val partitionData0 = PartitionConsumeData(partitionId = 0, nextOffset = 10L, records = emptyList())
            val partitionData3 = PartitionConsumeData(partitionId = 3, nextOffset = 20L, records = emptyList())

            whenever(mockFetcher.consumePartitionRecords(
                topicName = eq("test-topic"), partitionId = eq(0),
                timestamp = any(), offset = isNull(), maxPollRecords = isNull()
            )).thenReturn(partitionData0)

            whenever(mockFetcher.consumePartitionRecords(
                topicName = eq("test-topic"), partitionId = eq(3),
                timestamp = any(), offset = isNull(), maxPollRecords = isNull()
            )).thenReturn(partitionData3)

            val result = client.fetchInitialRecords(config, mockFetcher, "test-topic", partitionFilter = filter)

            verify(mockFetcher, never()).consumeRecords(any(), any())
            verify(mockFetcher).consumePartitionRecords(
                topicName = eq("test-topic"), partitionId = eq(0),
                timestamp = any(), offset = isNull(), maxPollRecords = isNull()
            )
            verify(mockFetcher).consumePartitionRecords(
                topicName = eq("test-topic"), partitionId = eq(3),
                timestamp = any(), offset = isNull(), maxPollRecords = isNull()
            )
            assertEquals(2, result.partitionDataList.size)
        }

        @Test
        fun `should throw for CONSUMER_GROUP start type`() {
            val config = buildConfig(startType = ConsumerStartType.CONSUMER_GROUP)

            val ex = assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    client.fetchInitialRecords(config, mockFetcher, "test-topic", partitionFilter = null)
                }
            }
            assertTrue(ex.message!!.contains("Consumer group"))
        }

        @Test
        fun `should throw for CONSUMER_GROUP start type even with partition filter`() {
            val config = buildConfig(startType = ConsumerStartType.CONSUMER_GROUP)

            val ex = assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    client.fetchInitialRecords(config, mockFetcher, "test-topic", partitionFilter = setOf(0))
                }
            }
            assertTrue(ex.message!!.contains("Consumer group"))
        }

        @Test
        fun `should use POST with fromBeginning=false for NOW when unfiltered`() = runBlocking {
            val config = buildConfig(startType = ConsumerStartType.NOW)
            whenever(mockFetcher.consumeRecords(eq("test-topic"), any())).thenReturn(emptyResponse)

            client.fetchInitialRecords(config, mockFetcher, "test-topic", partitionFilter = null)

            val captor = argumentCaptor<ConsumeRecordsRequest>()
            verify(mockFetcher).consumeRecords(eq("test-topic"), captor.capture())
            assertEquals(false, captor.firstValue.fromBeginning)
            assertNull(captor.firstValue.offsets)
        }

        @Test
        fun `should propagate error when getPartitionOffset fails for filtered consume`() {
            val config = buildConfig(startType = ConsumerStartType.THE_BEGINNING)
            val filter = setOf(0)

            runBlocking {
                whenever(mockFetcher.getPartitionOffset("test-topic", 0, true))
                    .thenThrow(RuntimeException("Connection refused"))
            }

            assertThrows(RuntimeException::class.java) {
                runBlocking {
                    client.fetchInitialRecords(config, mockFetcher, "test-topic", partitionFilter = filter)
                }
            }
        }

        @Test
        fun `should propagate error when consumePartitionRecords fails for timestamp filtered consume`() {
            val config = buildConfig(startType = ConsumerStartType.LAST_HOUR)
            val filter = setOf(0)

            runBlocking {
                whenever(mockFetcher.consumePartitionRecords(
                    topicName = any(), partitionId = any(),
                    timestamp = any(), offset = isNull(), maxPollRecords = isNull()
                )).thenThrow(RuntimeException("Server error"))
            }

            assertThrows(RuntimeException::class.java) {
                runBlocking {
                    client.fetchInitialRecords(config, mockFetcher, "test-topic", partitionFilter = filter)
                }
            }
        }

        @Test
        fun `should throw when calculateStartTime returns null for SPECIFIC_DATE without time`() {
            // SPECIFIC_DATE with no time set → calculateStartTime returns null → error guard fires
            val startWithMap = mutableMapOf("type" to ConsumerStartType.SPECIFIC_DATE.name)
            val config = StorageConsumerConfig(topic = "test-topic", startWith = startWithMap)
            val filter = setOf(0)

            val ex = assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    client.fetchInitialRecords(config, mockFetcher, "test-topic", partitionFilter = filter)
                }
            }
            assertTrue(ex.message!!.contains("Failed to calculate start time"))
        }

        @Test
        fun `should pass resolvedMaxPollRecords to single-partition GET`() = runBlocking {
            val config = buildConfig(startType = ConsumerStartType.LAST_HOUR)
            val filter = setOf(0)
            client.resolvedMaxPollRecords = 42

            val partitionData = PartitionConsumeData(partitionId = 0, nextOffset = 10L, records = emptyList())
            whenever(mockFetcher.consumePartitionRecords(
                topicName = any(), partitionId = any(),
                timestamp = any(), offset = isNull(), maxPollRecords = any()
            )).thenReturn(partitionData)

            client.fetchInitialRecords(config, mockFetcher, "test-topic", partitionFilter = filter)

            verify(mockFetcher).consumePartitionRecords(
                topicName = eq("test-topic"), partitionId = eq(0),
                timestamp = any(), offset = isNull(), maxPollRecords = eq(42)
            )
            Unit
        }

        @Test
        fun `should include advanced settings in POST request for filtered offset-based consume`() = runBlocking {
            val config = buildConfig(startType = ConsumerStartType.THE_BEGINNING)
            val filter = setOf(0)
            client.resolvedMaxPollRecords = 200
            client.resolvedFetchMaxBytes = 2097152

            whenever(mockFetcher.getPartitionOffset("test-topic", 0, true)).thenReturn(0L)
            whenever(mockFetcher.consumeRecords(eq("test-topic"), any())).thenReturn(emptyResponse)

            client.fetchInitialRecords(config, mockFetcher, "test-topic", partitionFilter = filter)

            val captor = argumentCaptor<ConsumeRecordsRequest>()
            verify(mockFetcher).consumeRecords(eq("test-topic"), captor.capture())
            assertEquals(200, captor.firstValue.maxPollRecords)
            assertEquals(2097152, captor.firstValue.fetchMaxBytes)
            assertNotNull(captor.firstValue.offsets)
        }
    }

    @Nested
    @DisplayName("buildInitialConsumeRequest")
    inner class BuildInitialConsumeRequest {

        @Test
        fun `should use fromBeginning=true for THE_BEGINNING`() = runBlocking {
            val config = buildConfig(startType = ConsumerStartType.THE_BEGINNING)
            val request = client.buildInitialConsumeRequest(config, mockFetcher)
            assertEquals(true, request.fromBeginning)
            assertNull(request.offsets)
            assertNull(request.timestamp)
        }

        @Test
        fun `should use fromBeginning=false for NOW`() = runBlocking {
            val config = buildConfig(startType = ConsumerStartType.NOW)
            val request = client.buildInitialConsumeRequest(config, mockFetcher)
            assertEquals(false, request.fromBeginning)
            assertNull(request.offsets)
        }

        @Test
        fun `should add user offset to beginning offsets for OFFSET`() = runBlocking {
            val config = buildConfig(startType = ConsumerStartType.OFFSET, offset = 5L)
            whenever(mockFetcher.getTopicBeginningOffsets("test-topic")).thenReturn(mapOf(0 to 0L, 1 to 10L))

            val request = client.buildInitialConsumeRequest(config, mockFetcher)

            val offsetMap = request.offsets!!.associate { it.partitionId to it.offset }
            assertEquals(5L, offsetMap[0])   // 0 + 5
            assertEquals(15L, offsetMap[1])  // 10 + 5
        }

        @Test
        fun `should subtract from end offsets for LATEST_OFFSET_MINUS_X`() = runBlocking {
            val config = buildConfig(startType = ConsumerStartType.LATEST_OFFSET_MINUS_X, offset = -10L)
            whenever(mockFetcher.getTopicEndOffsets("test-topic")).thenReturn(mapOf(0 to 100L, 1 to 50L))

            val request = client.buildInitialConsumeRequest(config, mockFetcher)

            val offsetMap = request.offsets!!.associate { it.partitionId to it.offset }
            assertEquals(90L, offsetMap[0])   // max(0, 100 + (-10))
            assertEquals(40L, offsetMap[1])   // max(0, 50 + (-10))
        }

        @Test
        fun `should include timestamp for LAST_HOUR`() = runBlocking {
            val config = buildConfig(startType = ConsumerStartType.LAST_HOUR)
            val request = client.buildInitialConsumeRequest(config, mockFetcher)

            assertNotNull(request.timestamp)
            val oneHourAgo = System.currentTimeMillis() - 3_600_000L
            assertTrue(request.timestamp!! in (oneHourAgo - 5000)..(oneHourAgo + 5000))
        }

        @Test
        fun `should include advanced settings in request`() = runBlocking {
            val config = buildConfig(startType = ConsumerStartType.NOW)
            client.resolvedMaxPollRecords = 100
            client.resolvedFetchMaxBytes = 1048576

            val request = client.buildInitialConsumeRequest(config, mockFetcher)

            assertEquals(100, request.maxPollRecords)
            assertEquals(1048576, request.fetchMaxBytes)
        }
    }

    @Nested
    @DisplayName("convertToConsumerRecord")
    inner class ConvertToConsumerRecord {

        @BeforeEach
        fun setUpConfigs() {
            startWithConfigs()
        }

        private fun record(
            partitionId: Int = 0,
            offset: Long = 100L,
            timestamp: Long = 1700000000000L,
            timestampType: ApiTimestampType = ApiTimestampType.CREATE_TIME,
            headers: List<PartitionConsumeRecordHeader> = emptyList(),
            key: String? = "test-key",
            value: String? = "test-value"
        ): PartitionConsumeRecord {
            fun encodeRaw(s: String?) = if (s != null) {
                JsonObject(mapOf("__raw__" to JsonPrimitive(Base64.getEncoder().encodeToString(s.toByteArray()))))
            } else {
                JsonNull
            }

            return PartitionConsumeRecord(
                partitionId = partitionId,
                offset = offset,
                timestamp = timestamp,
                timestampType = timestampType,
                headers = headers,
                key = encodeRaw(key),
                value = encodeRaw(value)
            )
        }

        @Test
        fun `should decode base64 header values to byte arrays`() = runBlocking {
            val headerValue = Base64.getEncoder().encodeToString("header-val".toByteArray())
            val rec = record(headers = listOf(PartitionConsumeRecordHeader("my-header", headerValue)))

            val result = client.convertToConsumerRecord(rec, "test-topic", mockFetcher)

            val header = result.headers().lastHeader("my-header")
            assertNotNull(header)
            assertArrayEquals("header-val".toByteArray(), header.value())
        }

        @Test
        fun `should handle non-base64 header values as raw bytes`() = runBlocking {
            val rec = record(headers = listOf(PartitionConsumeRecordHeader("h", "not!valid!base64!@#\$")))

            val result = client.convertToConsumerRecord(rec, "test-topic", mockFetcher)

            val header = result.headers().lastHeader("h")
            assertNotNull(header)
            assertArrayEquals("not!valid!base64!@#\$".toByteArray(), header.value())
        }

        @Test
        fun `should map CREATE_TIME timestamp type correctly`() = runBlocking {
            val rec = record(timestampType = ApiTimestampType.CREATE_TIME)
            val result = client.convertToConsumerRecord(rec, "test-topic", mockFetcher)
            assertEquals(TimestampType.CREATE_TIME, result.timestampType())
        }

        @Test
        fun `should map LOG_APPEND_TIME timestamp type correctly`() = runBlocking {
            val rec = record(timestampType = ApiTimestampType.LOG_APPEND_TIME)
            val result = client.convertToConsumerRecord(rec, "test-topic", mockFetcher)
            assertEquals(TimestampType.LOG_APPEND_TIME, result.timestampType())
        }

        @Test
        fun `should map NO_TIMESTAMP_TYPE correctly`() = runBlocking {
            val rec = record(timestampType = ApiTimestampType.NO_TIMESTAMP_TYPE)
            val result = client.convertToConsumerRecord(rec, "test-topic", mockFetcher)
            assertEquals(TimestampType.NO_TIMESTAMP_TYPE, result.timestampType())
        }

        @Test
        fun `should set correct partition, offset, and timestamp`() = runBlocking {
            val rec = record(partitionId = 3, offset = 42L, timestamp = 1700000000000L)
            val result = client.convertToConsumerRecord(rec, "my-topic", mockFetcher)

            assertEquals("my-topic", result.topic())
            assertEquals(3, result.partition())
            assertEquals(42L, result.offset())
            assertEquals(1700000000000L, result.timestamp())
        }

        @Test
        fun `should set correct serialized key and value sizes`() = runBlocking {
            val rec = record(key = "abc", value = "defgh")
            val result = client.convertToConsumerRecord(rec, "test-topic", mockFetcher)

            assertEquals(3, result.serializedKeySize())
            assertEquals(5, result.serializedValueSize())
        }

        @Test
        fun `should handle null key and value`() = runBlocking {
            val rec = record(key = null, value = null)
            val result = client.convertToConsumerRecord(rec, "test-topic", mockFetcher)

            assertNull(result.key())
            assertNull(result.value())
        }

        @Test
        fun `should deserialize key and value as strings by default`() = runBlocking {
            val rec = record(key = "my-key", value = "my-value")
            val result = client.convertToConsumerRecord(rec, "test-topic", mockFetcher)

            assertEquals("my-key", result.key())
            assertEquals("my-value", result.value())
        }

        @Test
        fun `should handle multiple headers`() = runBlocking {
            val h1Val = Base64.getEncoder().encodeToString("v1".toByteArray())
            val h2Val = Base64.getEncoder().encodeToString("v2".toByteArray())
            val rec = record(headers = listOf(
                PartitionConsumeRecordHeader("h1", h1Val),
                PartitionConsumeRecordHeader("h2", h2Val)
            ))

            val result = client.convertToConsumerRecord(rec, "test-topic", mockFetcher)

            assertEquals(2, result.headers().toArray().size)
            assertArrayEquals("v1".toByteArray(), result.headers().lastHeader("h1").value())
            assertArrayEquals("v2".toByteArray(), result.headers().lastHeader("h2").value())
        }
    }

    @Nested
    @DisplayName("ConsumeRecordsRequest serialization")
    inner class ConsumeRecordsRequestSerialization {

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

        @Test
        fun `should serialize maxPollRecords as max_poll_records`() {
            val request = ConsumeRecordsRequest(maxPollRecords = 100)
            val serialized = json.encodeToString(request)
            assertTrue(serialized.contains("\"max_poll_records\":100"), "Expected max_poll_records in: $serialized")
            assertFalse(serialized.contains("maxPollRecords"), "Should not contain Kotlin field name")
        }

        @Test
        fun `should serialize fetchMaxBytes as fetch_max_bytes`() {
            val request = ConsumeRecordsRequest(fetchMaxBytes = 1048576)
            val serialized = json.encodeToString(request)
            assertTrue(serialized.contains("\"fetch_max_bytes\":1048576"), "Expected fetch_max_bytes in: $serialized")
            assertFalse(serialized.contains("fetchMaxBytes"), "Should not contain Kotlin field name")
        }

        @Test
        fun `should omit null optional fields`() {
            val request = ConsumeRecordsRequest(fromBeginning = true)
            val serialized = json.encodeToString(request)
            assertFalse(serialized.contains("max_poll_records"), "Null maxPollRecords should be omitted")
            assertFalse(serialized.contains("fetch_max_bytes"), "Null fetchMaxBytes should be omitted")
            assertFalse(serialized.contains("timestamp"), "Null timestamp should be omitted")
            assertTrue(serialized.contains("\"from_beginning\":true"))
        }
    }

    @Nested
    @DisplayName("SinglePartitionConsumeResponse deserialization")
    inner class SinglePartitionConsumeResponseDeserialization {

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        @Test
        fun `should deserialize single partition response from JSON fixture`() {
            // Base64 decoded values in fixture:
            //   Record 0: key="test-key", value="test-value"
            //   Record 1: key="key-2", value="value-2", header trace-id="abc123"
            val jsonText = javaClass.getResourceAsStream(
                "/fixtures/consumer/single-partition-consume-response.json"
            )!!.readBytes().toString(Charsets.UTF_8)

            val response = json.decodeFromString<SinglePartitionConsumeResponse>(jsonText)

            assertEquals("lkc-test123", response.clusterId)
            assertEquals("test-topic", response.topicName)
            assertEquals(0, response.partitionData.partitionId)
            assertEquals(150L, response.partitionData.nextOffset)
            assertEquals(2, response.partitionData.records.size)

            val firstRecord = response.partitionData.records[0]
            assertEquals(0, firstRecord.partitionId)
            assertEquals(100L, firstRecord.offset)
            assertEquals(1700000000000L, firstRecord.timestamp)
            assertEquals(ApiTimestampType.CREATE_TIME, firstRecord.timestampType)
            assertTrue(firstRecord.headers.isEmpty())

            val secondRecord = response.partitionData.records[1]
            assertEquals(101L, secondRecord.offset)
            assertEquals(1, secondRecord.headers.size)
            assertEquals("trace-id", secondRecord.headers[0].key)
        }
    }
}
