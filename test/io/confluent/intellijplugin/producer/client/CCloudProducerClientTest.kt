package io.confluent.intellijplugin.producer.client

import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.ccloud.client.CCloudApiException
import io.confluent.intellijplugin.ccloud.fetcher.DataPlaneFetcher
import io.confluent.intellijplugin.ccloud.model.response.ProduceRecordRequest
import io.confluent.intellijplugin.ccloud.model.response.ProduceRecordResponse
import io.confluent.intellijplugin.ccloud.model.response.SchemaVersionResponse
import io.confluent.intellijplugin.common.models.KafkaFieldType
import io.confluent.intellijplugin.consumer.models.ConsumerProducerFieldConfig
import io.confluent.intellijplugin.core.settings.connections.Property
import io.confluent.intellijplugin.data.CCloudClusterDataManager
import io.confluent.intellijplugin.registry.KafkaRegistryFormat
import io.confluent.intellijplugin.registry.KafkaRegistryType
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchemaUtils
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaUtils
import com.google.protobuf.DynamicMessage
import io.confluent.kafka.schemaregistry.protobuf.MessageIndexes
import io.confluent.intellijplugin.ccloud.model.response.PartitionData
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Base64

@TestApplication
class CCloudProducerClientTest {

    private lateinit var client: CCloudProducerClient
    private lateinit var mockFetcher: DataPlaneFetcher

    @BeforeEach
    fun setUp() {
        client = CCloudProducerClient(
            clusterDataManager = mock<CCloudClusterDataManager>(),
            onStart = {},
            onStop = {},
            baseBackoffMs = 1L,
            maxBackoffMs = 1L,
        )
        mockFetcher = mock<DataPlaneFetcher> {
            onBlocking { getLatestVersionInfo(any()) } doReturn SchemaVersionResponse(
                subject = "test-subject",
                version = 1,
                id = 100,
                schema = "{}",
                schemaType = "AVRO"
            )
        }
    }

    private fun createFieldConfig(
        type: KafkaFieldType,
        valueText: String = "",
        isKey: Boolean = false,
        topic: String = "test-topic"
    ) = ConsumerProducerFieldConfig(
        type = type,
        valueText = valueText,
        isKey = isKey,
        topic = topic,
        registryType = KafkaRegistryType.NONE,
        schemaName = "",
        schemaFormat = KafkaRegistryFormat.UNKNOWN,
        parsedSchema = null
    )

    @Nested
    @DisplayName("buildRecordData")
    inner class BuildRecordData {

        private val avroSchemaJson = """{"type": "record", "name": "Test", "fields": [{"name": "name", "type": "string"}]}"""
        private val protoSchemaText = javaClass.getResourceAsStream(
            "/fixtures/schemas/user-proto-schema.proto"
        )!!.readBytes().toString(Charsets.UTF_8)

        @Nested
        @DisplayName("primitive and simple types")
        inner class PrimitiveTypes {

            @Test
            fun `should build STRING type data`() {
                val field = createFieldConfig(KafkaFieldType.STRING, "hello")
                val data = runBlocking { client.buildRecordData(mockFetcher, field, "test-topic") }

                assertNotNull(data)
                assertEquals("STRING", data!!.type)
                assertEquals("hello", data.data)
            }

            @Test
            fun `should build JSON type data`() {
                val field = createFieldConfig(KafkaFieldType.JSON, """{"key": "value"}""")
                val data = runBlocking { client.buildRecordData(mockFetcher, field, "test-topic") }

                assertNotNull(data)
                assertEquals("JSON", data!!.type)
                assertEquals("""{"key": "value"}""", data.data)
            }

            @Test
            fun `should build STRING type with empty value`() {
                val field = createFieldConfig(KafkaFieldType.STRING, "")
                val data = runBlocking { client.buildRecordData(mockFetcher, field, "test-topic") }

                assertNotNull(data)
                assertEquals("STRING", data!!.type)
                assertEquals("", data.data)
            }

            @Test
            fun `should return null for NULL type`() {
                val field = createFieldConfig(KafkaFieldType.NULL)
                val data = runBlocking { client.buildRecordData(mockFetcher, field, "test-topic") }

                assertNull(data)
            }

            @Test
            fun `should build BINARY type for LONG`() {
                val field = createFieldConfig(KafkaFieldType.LONG, "12345")
                val data = runBlocking { client.buildRecordData(mockFetcher, field, "test-topic") }

                assertNotNull(data)
                assertEquals("BINARY", data!!.type)
                val decoded = Base64.getDecoder().decode(data.data)
                assertEquals(8, decoded.size)
            }

            @Test
            fun `should build BINARY type for INTEGER`() {
                val field = createFieldConfig(KafkaFieldType.INTEGER, "42")
                val data = runBlocking { client.buildRecordData(mockFetcher, field, "test-topic") }

                assertNotNull(data)
                assertEquals("BINARY", data!!.type)
                val decoded = Base64.getDecoder().decode(data.data)
                assertEquals(4, decoded.size)
            }

            @Test
            fun `should build BINARY type for DOUBLE`() {
                val field = createFieldConfig(KafkaFieldType.DOUBLE, "3.14")
                val data = runBlocking { client.buildRecordData(mockFetcher, field, "test-topic") }

                assertNotNull(data)
                assertEquals("BINARY", data!!.type)
                val decoded = Base64.getDecoder().decode(data.data)
                assertEquals(8, decoded.size)
            }

            @Test
            fun `should build BINARY type for FLOAT`() {
                val field = createFieldConfig(KafkaFieldType.FLOAT, "2.5")
                val data = runBlocking { client.buildRecordData(mockFetcher, field, "test-topic") }

                assertNotNull(data)
                assertEquals("BINARY", data!!.type)
                val decoded = Base64.getDecoder().decode(data.data)
                assertEquals(4, decoded.size)
            }

            @Test
            fun `should build BINARY type for BASE64`() {
                val inputBytes = byteArrayOf(1, 2, 3, 4)
                val inputBase64 = Base64.getEncoder().encodeToString(inputBytes)
                val field = createFieldConfig(KafkaFieldType.BASE64, inputBase64)
                val data = runBlocking { client.buildRecordData(mockFetcher, field, "test-topic") }

                assertNotNull(data)
                assertEquals("BINARY", data!!.type)
                val decoded = Base64.getDecoder().decode(data.data)
                assertArrayEquals(inputBytes, decoded)
            }
        }

        @Nested
        @DisplayName("Schema Registry types")
        inner class SchemaRegistryTypes {

            private val avroCache = mapOf("my-subject" to SchemaVersionResponse(
                subject = "my-subject", version = 1, id = 100, schema = "{}", schemaType = "AVRO"
            ))

            @Test
            fun `should build BINARY for SCHEMA_REGISTRY Avro using cached schema`() {
                val avroSchema = AvroSchema(avroSchemaJson)
                val field = ConsumerProducerFieldConfig(
                    type = KafkaFieldType.SCHEMA_REGISTRY,
                    valueText = """{"name": "test"}""",
                    isKey = false,
                    topic = "test-topic",
                    registryType = KafkaRegistryType.CONFLUENT,
                    schemaName = "my-subject",
                    schemaFormat = KafkaRegistryFormat.AVRO,
                    parsedSchema = avroSchema
                )
                val data = runBlocking { client.buildRecordData(mockFetcher, field, "test-topic", avroCache) }

                assertNotNull(data)
                assertEquals("BINARY", data!!.type)
                val decoded = Base64.getDecoder().decode(data.data)
                assertTrue(decoded.size > 5)
                assertEquals(0x00.toByte(), decoded[0])
                val schemaId = java.nio.ByteBuffer.wrap(decoded, 1, 4).getInt()
                assertEquals(100, schemaId)

                runBlocking { verify(mockFetcher, never()).getLatestVersionInfo(any()) }
            }

            @Test
            fun `should build BINARY for SCHEMA_REGISTRY JSON Schema using cached schema`() {
                val jsonCache = mapOf("json-subject" to SchemaVersionResponse(
                    subject = "json-subject", version = 1, id = 100, schema = "{}", schemaType = "JSON"
                ))
                val field = ConsumerProducerFieldConfig(
                    type = KafkaFieldType.SCHEMA_REGISTRY,
                    valueText = """{"key": "value"}""",
                    isKey = false,
                    topic = "test-topic",
                    registryType = KafkaRegistryType.CONFLUENT,
                    schemaName = "json-subject",
                    schemaFormat = KafkaRegistryFormat.JSON,
                    parsedSchema = null
                )
                val data = runBlocking { client.buildRecordData(mockFetcher, field, "test-topic", jsonCache) }

                assertNotNull(data)
                assertEquals("BINARY", data!!.type)
                val decoded = Base64.getDecoder().decode(data.data)
                assertEquals(0x00.toByte(), decoded[0])
                val schemaId = java.nio.ByteBuffer.wrap(decoded, 1, 4).getInt()
                assertEquals(100, schemaId)
                val jsonPayload = String(decoded, 5, decoded.size - 5, Charsets.UTF_8)
                assertEquals("""{"key": "value"}""", jsonPayload)

                runBlocking { verify(mockFetcher, never()).getLatestVersionInfo(any()) }
            }

            @Test
            fun `should build BINARY for SCHEMA_REGISTRY Protobuf using cached schema`() {
                val protoCache = mapOf("proto-subject" to SchemaVersionResponse(
                    subject = "proto-subject", version = 1, id = 100, schema = "{}", schemaType = "PROTOBUF"
                ))
                val protobufSchema = ProtobufSchema(protoSchemaText)
                val field = ConsumerProducerFieldConfig(
                    type = KafkaFieldType.SCHEMA_REGISTRY,
                    valueText = """{"name": "Bob", "age": 25}""",
                    isKey = false,
                    topic = "test-topic",
                    registryType = KafkaRegistryType.CONFLUENT,
                    schemaName = "proto-subject",
                    schemaFormat = KafkaRegistryFormat.PROTOBUF,
                    parsedSchema = protobufSchema
                )
                val data = runBlocking { client.buildRecordData(mockFetcher, field, "test-topic", protoCache) }

                assertNotNull(data)
                assertEquals("BINARY", data!!.type)
                val decoded = Base64.getDecoder().decode(data.data)
                assertEquals(0x00.toByte(), decoded[0])
                val schemaId = java.nio.ByteBuffer.wrap(decoded, 1, 4).getInt()
                assertEquals(100, schemaId)
                val payload = decoded.copyOfRange(5, decoded.size)
                val indexBytes = MessageIndexes(listOf(0)).toByteArray()
                val protoBytes = payload.copyOfRange(indexBytes.size, payload.size)
                val descriptor = protobufSchema.toDescriptor()!!
                val message = DynamicMessage.parseFrom(descriptor, protoBytes)
                assertEquals("Bob", message.getField(descriptor.findFieldByName("name")))
                assertEquals(25, message.getField(descriptor.findFieldByName("age")))

                runBlocking { verify(mockFetcher, never()).getLatestVersionInfo(any()) }
            }

            @Test
            fun `should throw on SCHEMA_REGISTRY with UNKNOWN format`() {
                val unknownCache = mapOf("unknown-subject" to SchemaVersionResponse(
                    subject = "unknown-subject", version = 1, id = 1, schema = "{}", schemaType = null
                ))
                val field = ConsumerProducerFieldConfig(
                    type = KafkaFieldType.SCHEMA_REGISTRY,
                    valueText = "some data",
                    isKey = false,
                    topic = "test-topic",
                    registryType = KafkaRegistryType.CONFLUENT,
                    schemaName = "unknown-subject",
                    schemaFormat = KafkaRegistryFormat.UNKNOWN,
                    parsedSchema = null
                )

                assertThrows(IllegalStateException::class.java) {
                    runBlocking { client.buildRecordData(mockFetcher, field, "test-topic", unknownCache) }
                }
            }

            @Test
            fun `should fall back to fetcher when subject is not in cache`() = runBlocking {
                val cache = mapOf("other-subject" to SchemaVersionResponse(
                    subject = "other-subject", version = 1, id = 42, schema = "{}", schemaType = "JSON"
                ))
                val field = ConsumerProducerFieldConfig(
                    type = KafkaFieldType.SCHEMA_REGISTRY,
                    valueText = """{"key": "value"}""",
                    isKey = false,
                    topic = "test-topic",
                    registryType = KafkaRegistryType.CONFLUENT,
                    schemaName = "my-subject",
                    schemaFormat = KafkaRegistryFormat.JSON,
                    parsedSchema = null
                )

                val data = client.buildRecordData(mockFetcher, field, "test-topic", cache)

                assertNotNull(data)
                val decoded = Base64.getDecoder().decode(data!!.data)
                assertEquals(100, java.nio.ByteBuffer.wrap(decoded, 1, 4).getInt())
                verify(mockFetcher, times(1)).getLatestVersionInfo("my-subject")
                Unit
            }

            @Test
            fun `should reuse cache across repeated calls without additional fetcher calls`() = runBlocking {
                val cache = mapOf("my-subject" to SchemaVersionResponse(
                    subject = "my-subject", version = 1, id = 42, schema = "{}", schemaType = "JSON"
                ))
                val field = ConsumerProducerFieldConfig(
                    type = KafkaFieldType.SCHEMA_REGISTRY,
                    valueText = """{"key": "value"}""",
                    isKey = false,
                    topic = "test-topic",
                    registryType = KafkaRegistryType.CONFLUENT,
                    schemaName = "my-subject",
                    schemaFormat = KafkaRegistryFormat.JSON,
                    parsedSchema = null
                )

                repeat(5) {
                    client.buildRecordData(mockFetcher, field, "test-topic", cache)
                }

                verify(mockFetcher, never()).getLatestVersionInfo(any())
                Unit
            }
        }

        @Nested
        @DisplayName("custom schema types")
        inner class CustomSchemaTypes {

            @Test
            fun `should build BINARY for AVRO_CUSTOM`() {
                val avroSchema = AvroSchema(avroSchemaJson)
                val field = ConsumerProducerFieldConfig(
                    type = KafkaFieldType.AVRO_CUSTOM,
                    valueText = """{"name": "test"}""",
                    isKey = false,
                    topic = "test-topic",
                    registryType = KafkaRegistryType.NONE,
                    schemaName = "",
                    schemaFormat = KafkaRegistryFormat.AVRO,
                    parsedSchema = avroSchema
                )
                val data = runBlocking { client.buildRecordData(mockFetcher, field, "test-topic") }

                assertNotNull(data)
                assertEquals("BINARY", data!!.type)
                val decoded = Base64.getDecoder().decode(data.data)
                val reader = org.apache.avro.generic.GenericDatumReader<Any>(avroSchema.rawSchema())
                val decoder = org.apache.avro.io.DecoderFactory.get().binaryDecoder(decoded, null)
                val result = reader.read(null, decoder) as org.apache.avro.generic.GenericRecord
                assertEquals("test", result.get("name").toString())
            }

            @Test
            fun `should build BINARY for PROTOBUF_CUSTOM`() {
                val protobufSchema = ProtobufSchema(protoSchemaText)
                val field = ConsumerProducerFieldConfig(
                    type = KafkaFieldType.PROTOBUF_CUSTOM,
                    valueText = """{"name": "Alice", "age": 30}""",
                    isKey = false,
                    topic = "test-topic",
                    registryType = KafkaRegistryType.NONE,
                    schemaName = "",
                    schemaFormat = KafkaRegistryFormat.PROTOBUF,
                    parsedSchema = protobufSchema
                )
                val data = runBlocking { client.buildRecordData(mockFetcher, field, "test-topic") }

                assertNotNull(data)
                assertEquals("BINARY", data!!.type)
                val decoded = Base64.getDecoder().decode(data.data)
                val descriptor = protobufSchema.toDescriptor()!!
                val message = DynamicMessage.parseFrom(descriptor, decoded)
                assertEquals("Alice", message.getField(descriptor.findFieldByName("name")))
                assertEquals(30, message.getField(descriptor.findFieldByName("age")))
            }
        }
    }

    @Nested
    @DisplayName("buildProduceRequest")
    inner class BuildProduceRequest {

        @Test
        fun `should build request with partition`() {
            val key = createFieldConfig(KafkaFieldType.STRING, "k", isKey = true)
            val value = createFieldConfig(KafkaFieldType.STRING, "v")
            val request = runBlocking { client.buildProduceRequest(mockFetcher, key, value, emptyList(), "test-topic", 5) }

            assertEquals(5, request.partitionId)
        }

        @Test
        fun `should build request without partition when negative`() {
            val key = createFieldConfig(KafkaFieldType.STRING, "k", isKey = true)
            val value = createFieldConfig(KafkaFieldType.STRING, "v")
            val request = runBlocking { client.buildProduceRequest(mockFetcher, key, value, emptyList(), "test-topic", -1) }

            assertNull(request.partitionId)
        }

        @Test
        fun `should include headers as base64`() {
            val key = createFieldConfig(KafkaFieldType.STRING, "k", isKey = true)
            val value = createFieldConfig(KafkaFieldType.STRING, "v")
            val headers = listOf(
                Property("header-key", "header-value")
            )
            val request = runBlocking { client.buildProduceRequest(mockFetcher, key, value, headers, "test-topic", -1) }

            assertNotNull(request.headers)
            assertEquals(1, request.headers!!.size)
            assertEquals("header-key", request.headers!![0].name)
            val decodedValue = String(Base64.getDecoder().decode(request.headers!![0].value))
            assertEquals("header-value", decodedValue)
        }

        @Test
        fun `should handle header with null name`() {
            val key = createFieldConfig(KafkaFieldType.STRING, "k", isKey = true)
            val value = createFieldConfig(KafkaFieldType.STRING, "v")
            val headers = listOf(Property(null, "header-value"))
            val request = runBlocking { client.buildProduceRequest(mockFetcher, key, value, headers, "test-topic", -1) }

            assertNotNull(request.headers)
            assertEquals("", request.headers!![0].name)
        }

        @Test
        fun `should handle header with null value`() {
            val key = createFieldConfig(KafkaFieldType.STRING, "k", isKey = true)
            val value = createFieldConfig(KafkaFieldType.STRING, "v")
            val headers = listOf(Property("header-key", null))
            val request = runBlocking { client.buildProduceRequest(mockFetcher, key, value, headers, "test-topic", -1) }

            assertNotNull(request.headers)
            assertEquals("header-key", request.headers!![0].name)
            assertNull(request.headers!![0].value)
        }

        @Test
        fun `should omit headers when empty`() {
            val key = createFieldConfig(KafkaFieldType.STRING, "k", isKey = true)
            val value = createFieldConfig(KafkaFieldType.STRING, "v")
            val request = runBlocking { client.buildProduceRequest(mockFetcher, key, value, emptyList(), "test-topic", -1) }

            assertNull(request.headers)
        }

        @Test
        fun `should set null key for NULL type`() {
            val key = createFieldConfig(KafkaFieldType.NULL, "", isKey = true)
            val value = createFieldConfig(KafkaFieldType.STRING, "v")
            val request = runBlocking { client.buildProduceRequest(mockFetcher, key, value, emptyList(), "test-topic", -1) }

            assertNull(request.key)
            assertNotNull(request.value)
        }

        @Test
        fun `should pass schema cache through to both key and value`() {
            val keyCache = SchemaVersionResponse(
                subject = "key-subject", version = 1, id = 10, schema = "{}", schemaType = "JSON"
            )
            val valueCache = SchemaVersionResponse(
                subject = "value-subject", version = 1, id = 20, schema = "{}", schemaType = "JSON"
            )
            val cache = mapOf("key-subject" to keyCache, "value-subject" to valueCache)

            val key = ConsumerProducerFieldConfig(
                type = KafkaFieldType.SCHEMA_REGISTRY,
                valueText = """{"k": 1}""",
                isKey = true,
                topic = "test-topic",
                registryType = KafkaRegistryType.CONFLUENT,
                schemaName = "key-subject",
                schemaFormat = KafkaRegistryFormat.JSON,
                parsedSchema = null
            )
            val value = ConsumerProducerFieldConfig(
                type = KafkaFieldType.SCHEMA_REGISTRY,
                valueText = """{"v": 2}""",
                isKey = false,
                topic = "test-topic",
                registryType = KafkaRegistryType.CONFLUENT,
                schemaName = "value-subject",
                schemaFormat = KafkaRegistryFormat.JSON,
                parsedSchema = null
            )

            val request = runBlocking {
                client.buildProduceRequest(mockFetcher, key, value, emptyList(), "test-topic", -1, cache)
            }

            val keyDecoded = Base64.getDecoder().decode(request.key!!.data)
            assertEquals(10, java.nio.ByteBuffer.wrap(keyDecoded, 1, 4).getInt())

            val valueDecoded = Base64.getDecoder().decode(request.value!!.data)
            assertEquals(20, java.nio.ByteBuffer.wrap(valueDecoded, 1, 4).getInt())

            runBlocking { verify(mockFetcher, never()).getLatestVersionInfo(any()) }
        }
    }

    @Nested
    @DisplayName("serializePrimitive")
    inner class SerializePrimitive {

        @Test
        fun `should serialize STRING to bytes`() {
            val bytes = client.serializePrimitive(KafkaFieldType.STRING, "test-topic", "hello")
            assertEquals("hello", String(bytes))
        }

        @Test
        fun `should serialize LONG to 8 bytes`() {
            val bytes = client.serializePrimitive(KafkaFieldType.LONG, "test-topic", 1L)
            assertEquals(8, bytes.size)
            val value = java.nio.ByteBuffer.wrap(bytes).getLong()
            assertEquals(1L, value)
        }

        @Test
        fun `should serialize INTEGER to 4 bytes`() {
            val bytes = client.serializePrimitive(KafkaFieldType.INTEGER, "test-topic", 42)
            assertEquals(4, bytes.size)
            val value = java.nio.ByteBuffer.wrap(bytes).getInt()
            assertEquals(42, value)
        }

        @Test
        fun `should serialize DOUBLE to 8 bytes`() {
            val bytes = client.serializePrimitive(KafkaFieldType.DOUBLE, "test-topic", 3.14)
            assertEquals(8, bytes.size)
        }

        @Test
        fun `should serialize FLOAT to 4 bytes`() {
            val bytes = client.serializePrimitive(KafkaFieldType.FLOAT, "test-topic", 2.5f)
            assertEquals(4, bytes.size)
        }
    }

    @Nested
    @DisplayName("validatePartition")
    inner class ValidatePartition {

        private val fetcher = mock<DataPlaneFetcher>()

        private fun partitions(vararg ids: Int) = ids.map { PartitionData(partitionId = it) }

        @Test
        fun `should return partition when it exists`() = runBlocking {
            whenever(fetcher.describeTopicPartitions("test-topic"))
                .thenReturn(partitions(0, 1, 2))

            val result = client.validatePartition(2, "test-topic", fetcher)

            assertEquals(2, result)
        }

        @Test
        fun `should throw when partition does not exist`() {
            runBlocking {
                whenever(fetcher.describeTopicPartitions("test-topic"))
                    .thenReturn(partitions(0, 1, 2))
            }

            assertThrows(IllegalStateException::class.java) {
                runBlocking { client.validatePartition(5, "test-topic", fetcher) }
            }
        }

        @Test
        fun `should skip validation when partition is negative`() = runBlocking {
            // negative partition defaults to null / server side default (all partitions)
            val result = client.validatePartition(-1, "test-topic", fetcher)

            assertEquals(-1, result)
        }
    }

    @Nested
    @DisplayName("serializeAvro")
    inner class SerializeAvroTest {

        @Test
        fun `should serialize and be deserializable`() {
            val schemaJson = """{"type": "record", "name": "Test", "fields": [{"name": "name", "type": "string"}]}"""
            val avroSchema = AvroSchema(schemaJson)
            val record = AvroSchemaUtils.toObject("""{"name": "hello"}""", avroSchema)

            val bytes = client.serializeAvro(record, avroSchema)

            // Verify bytes can be deserialized back (mirror of CCloudConsumerClient.deserializeAvro)
            val reader = org.apache.avro.generic.GenericDatumReader<Any>(avroSchema.rawSchema())
            val decoder = org.apache.avro.io.DecoderFactory.get().binaryDecoder(bytes, null)
            val result = reader.read(null, decoder) as org.apache.avro.generic.GenericRecord
            assertEquals("hello", result.get("name").toString())
        }
    }

    @Nested
    @DisplayName("serializeProtobuf")
    inner class SerializeProtobufTest {

        private val protoSchemaText = javaClass.getResourceAsStream(
            "/fixtures/schemas/user-proto-schema.proto"
        )!!.readBytes().toString(Charsets.UTF_8)

        @Test
        fun `should serialize with message indexes and be deserializable`() {
            val schema = ProtobufSchema(protoSchemaText)
            val descriptor = schema.toDescriptor()!!
            val original = DynamicMessage.newBuilder(descriptor)
                .setField(descriptor.findFieldByName("name"), "Alice")
                .setField(descriptor.findFieldByName("age"), 30)
                .build()

            val bytes = client.serializeProtobuf(original)

            // Should start with message indexes (same as consumer expects)
            val indexBytes = MessageIndexes(listOf(0)).toByteArray()
            val protoBytes = bytes.copyOfRange(indexBytes.size, bytes.size)
            val result = DynamicMessage.parseFrom(descriptor, protoBytes)
            assertEquals("Alice", result.getField(descriptor.findFieldByName("name")))
            assertEquals(30, result.getField(descriptor.findFieldByName("age")))
        }
    }

    @Nested
    @DisplayName("Initial state")
    inner class InitialStateTests {

        @Test
        fun `should not be running initially`() {
            assertFalse(client.isRunning())
        }
    }

    @Nested
    @DisplayName("stop()")
    inner class StopTests {

        @Test
        fun `stop should be safe when no job is running`() {
            client.stop()
            assertFalse(client.isRunning())
        }

        @Test
        fun `stop can be called multiple times safely`() {
            client.stop()
            client.stop()
            assertFalse(client.isRunning())
        }
    }

    @Nested
    @DisplayName("isRetryableStatus")
    inner class IsRetryableStatus {

        @Test
        fun `should retry on 429 rate limit`() {
            assertTrue(client.isRetryableStatus(429))
        }

        @Test
        fun `should retry on 500 server error`() {
            assertTrue(client.isRetryableStatus(500))
        }

        @Test
        fun `should retry on 502 bad gateway`() {
            assertTrue(client.isRetryableStatus(502))
        }

        @Test
        fun `should retry on 503 service unavailable`() {
            assertTrue(client.isRetryableStatus(503))
        }

        @Test
        fun `should not retry on 400 bad request`() {
            assertFalse(client.isRetryableStatus(400))
        }

        @Test
        fun `should not retry on 401 unauthorized`() {
            assertFalse(client.isRetryableStatus(401))
        }

        @Test
        fun `should not retry on 403 forbidden`() {
            assertFalse(client.isRetryableStatus(403))
        }

        @Test
        fun `should not retry on 404 not found`() {
            assertFalse(client.isRetryableStatus(404))
        }

        @Test
        fun `should not retry on 422 unprocessable`() {
            assertFalse(client.isRetryableStatus(422))
        }
    }

    @Nested
    @DisplayName("dispose()")
    inner class DisposeTests {

        @Test
        fun `dispose should stop the client`() {
            client.dispose()
            assertFalse(client.isRunning())
        }
    }

    @Nested
    @DisplayName("produceWithRetry")
    inner class ProduceWithRetryTests {

        private val request = ProduceRecordRequest(
            key = null,
            value = null
        )

        private fun successResponse() = ProduceRecordResponse(
            errorCode = 200,
            topicName = "test-topic",
            partitionId = 0,
            offset = 1
        )

        @BeforeEach
        fun setUpRunning() {
            client.running.set(true)
        }

        @Test
        fun `should return response on first successful attempt`() = runBlocking {
            whenever(mockFetcher.produceRecord(any(), any())).thenReturn(successResponse())

            val (response, _) = client.produceWithRetry(mockFetcher, "test-topic", request)

            assertEquals(200, response.errorCode)
            verify(mockFetcher, times(1)).produceRecord(any(), any())
            Unit
        }

        @Test
        fun `should return response when errorCode is null`() = runBlocking {
            whenever(mockFetcher.produceRecord(any(), any()))
                .thenReturn(ProduceRecordResponse(errorCode = null, topicName = "test-topic"))

            val (response, _) = client.produceWithRetry(mockFetcher, "test-topic", request)

            assertNull(response.errorCode)
            verify(mockFetcher, times(1)).produceRecord(any(), any())
            Unit
        }

        @Test
        fun `should retry on retryable application-level error and succeed`() = runBlocking {
            whenever(mockFetcher.produceRecord(any(), any()))
                .thenReturn(ProduceRecordResponse(errorCode = 503, message = "Service Unavailable"))
                .thenReturn(successResponse())

            val (response, _) = client.produceWithRetry(mockFetcher, "test-topic", request)

            assertEquals(200, response.errorCode)
            verify(mockFetcher, times(2)).produceRecord(any(), any())
            Unit
        }

        @Test
        fun `should retry on HTTP-level CCloudApiException and succeed`() = runBlocking {
            var callCount = 0
            whenever(mockFetcher.produceRecord(any(), any())).thenAnswer {
                callCount++
                if (callCount == 1) throw CCloudApiException("Rate limited", 429)
                successResponse()
            }

            val (response, _) = client.produceWithRetry(mockFetcher, "test-topic", request)

            assertEquals(200, response.errorCode)
            verify(mockFetcher, times(2)).produceRecord(any(), any())
            Unit
        }

        @Test
        fun `should fail immediately on non-retryable application-level error`() {
            runBlocking {
                whenever(mockFetcher.produceRecord(any(), any()))
                    .thenReturn(ProduceRecordResponse(errorCode = 400, message = "Bad Request"))
            }

            val exception = assertThrows(CCloudApiException::class.java) {
                runBlocking { client.produceWithRetry(mockFetcher, "test-topic", request) }
            }
            assertEquals(400, exception.statusCode)
        }

        @Test
        fun `should fail immediately on non-retryable HTTP-level exception`() {
            runBlocking {
                whenever(mockFetcher.produceRecord(any(), any())).thenAnswer {
                    throw CCloudApiException("Forbidden", 403)
                }
            }

            val exception = assertThrows(CCloudApiException::class.java) {
                runBlocking { client.produceWithRetry(mockFetcher, "test-topic", request) }
            }
            assertEquals(403, exception.statusCode)
        }

        @Test
        fun `should throw after exhausting all retries`() {
            runBlocking {
                whenever(mockFetcher.produceRecord(any(), any())).thenAnswer {
                    throw CCloudApiException("Server Error", 500)
                }
            }

            val exception = assertThrows(CCloudApiException::class.java) {
                runBlocking { client.produceWithRetry(mockFetcher, "test-topic", request) }
            }
            assertEquals(500, exception.statusCode)
            // Should have attempted MAX_RETRIES (5) times
            runBlocking { verify(mockFetcher, times(5)).produceRecord(any(), any()) }
        }

        @Test
        fun `should retry on 429 HTTP exception then fail on 400 application error`() {
            var callCount = 0
            runBlocking {
                whenever(mockFetcher.produceRecord(any(), any())).thenAnswer {
                    callCount++
                    if (callCount == 1) throw CCloudApiException("Rate limited", 429)
                    ProduceRecordResponse(errorCode = 400, message = "Bad Request")
                }
            }

            val exception = assertThrows(CCloudApiException::class.java) {
                runBlocking { client.produceWithRetry(mockFetcher, "test-topic", request) }
            }
            assertEquals(400, exception.statusCode)
            runBlocking { verify(mockFetcher, times(2)).produceRecord(any(), any()) }
        }
    }
}
