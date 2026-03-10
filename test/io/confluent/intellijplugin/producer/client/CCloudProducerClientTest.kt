package io.confluent.intellijplugin.producer.client

import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.common.models.KafkaFieldType
import io.confluent.intellijplugin.consumer.models.ConsumerProducerFieldConfig
import io.confluent.intellijplugin.core.settings.connections.Property
import io.confluent.intellijplugin.data.CCloudClusterDataManager
import io.confluent.intellijplugin.registry.KafkaRegistryFormat
import io.confluent.intellijplugin.registry.KafkaRegistryType
import io.confluent.intellijplugin.ccloud.fetcher.DataPlaneFetcher
import io.confluent.intellijplugin.ccloud.model.response.PartitionData
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Base64

@TestApplication
class CCloudProducerClientTest {

    private lateinit var client: CCloudProducerClient

    @BeforeEach
    fun setUp() {
        client = CCloudProducerClient(
            clusterDataManager = mock<CCloudClusterDataManager>(),
            onStart = {},
            onStop = {}
        )
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

        @Test
        fun `should build STRING type data`() {
            val field = createFieldConfig(KafkaFieldType.STRING, "hello")
            val data = client.buildRecordData(field, "test-topic")

            assertNotNull(data)
            assertEquals("STRING", data!!.type)
            assertEquals("hello", data.data)
        }

        @Test
        fun `should build JSON type data`() {
            val field = createFieldConfig(KafkaFieldType.JSON, """{"key": "value"}""")
            val data = client.buildRecordData(field, "test-topic")

            assertNotNull(data)
            assertEquals("JSON", data!!.type)
            assertEquals("""{"key": "value"}""", data.data)
        }

        @Test
        fun `should build STRING type with empty value`() {
            val field = createFieldConfig(KafkaFieldType.STRING, "")
            val data = client.buildRecordData(field, "test-topic")

            assertNotNull(data)
            assertEquals("STRING", data!!.type)
            assertEquals("", data.data)
        }

        @Test
        fun `should return null for NULL type`() {
            val field = createFieldConfig(KafkaFieldType.NULL)
            val data = client.buildRecordData(field, "test-topic")

            assertNull(data)
        }

        @Test
        fun `should build BINARY type for LONG`() {
            val field = createFieldConfig(KafkaFieldType.LONG, "12345")
            val data = client.buildRecordData(field, "test-topic")

            assertNotNull(data)
            assertEquals("BINARY", data!!.type)
            // Verify base64 data decodes to 8-byte long
            val decoded = Base64.getDecoder().decode(data.data)
            assertEquals(8, decoded.size)
        }

        @Test
        fun `should build BINARY type for INTEGER`() {
            val field = createFieldConfig(KafkaFieldType.INTEGER, "42")
            val data = client.buildRecordData(field, "test-topic")

            assertNotNull(data)
            assertEquals("BINARY", data!!.type)
            val decoded = Base64.getDecoder().decode(data.data)
            assertEquals(4, decoded.size)
        }

        @Test
        fun `should build BINARY type for DOUBLE`() {
            val field = createFieldConfig(KafkaFieldType.DOUBLE, "3.14")
            val data = client.buildRecordData(field, "test-topic")

            assertNotNull(data)
            assertEquals("BINARY", data!!.type)
            val decoded = Base64.getDecoder().decode(data.data)
            assertEquals(8, decoded.size)
        }

        @Test
        fun `should build BINARY type for FLOAT`() {
            val field = createFieldConfig(KafkaFieldType.FLOAT, "2.5")
            val data = client.buildRecordData(field, "test-topic")

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
            val data = client.buildRecordData(field, "test-topic")

            assertNotNull(data)
            assertEquals("BINARY", data!!.type)
            val decoded = Base64.getDecoder().decode(data.data)
            assertArrayEquals(inputBytes, decoded)
        }
    }

    @Nested
    @DisplayName("buildProduceRequest")
    inner class BuildProduceRequest {

        @Test
        fun `should build request with partition`() {
            val key = createFieldConfig(KafkaFieldType.STRING, "k", isKey = true)
            val value = createFieldConfig(KafkaFieldType.STRING, "v")
            val request = client.buildProduceRequest(key, value,emptyList(), "test-topic", 5)

            assertEquals(5, request.partitionId)
        }

        @Test
        fun `should build request without partition when negative`() {
            val key = createFieldConfig(KafkaFieldType.STRING, "k", isKey = true)
            val value = createFieldConfig(KafkaFieldType.STRING, "v")
            val request = client.buildProduceRequest(key, value,emptyList(), "test-topic", -1)

            assertNull(request.partitionId)
        }

        @Test
        fun `should include headers as base64`() {
            val key = createFieldConfig(KafkaFieldType.STRING, "k", isKey = true)
            val value = createFieldConfig(KafkaFieldType.STRING, "v")
            val headers = listOf(
                Property("header-key", "header-value")
            )
            val request = client.buildProduceRequest(key, value,headers, "test-topic", -1)

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
            val request = client.buildProduceRequest(key, value, headers, "test-topic", -1)

            assertNotNull(request.headers)
            assertEquals("", request.headers!![0].name)
        }

        @Test
        fun `should handle header with null value`() {
            val key = createFieldConfig(KafkaFieldType.STRING, "k", isKey = true)
            val value = createFieldConfig(KafkaFieldType.STRING, "v")
            val headers = listOf(Property("header-key", null))
            val request = client.buildProduceRequest(key, value, headers, "test-topic", -1)

            assertNotNull(request.headers)
            assertEquals("header-key", request.headers!![0].name)
            assertNull(request.headers!![0].value)
        }

        @Test
        fun `should omit headers when empty`() {
            val key = createFieldConfig(KafkaFieldType.STRING, "k", isKey = true)
            val value = createFieldConfig(KafkaFieldType.STRING, "v")
            val request = client.buildProduceRequest(key, value,emptyList(), "test-topic", -1)

            assertNull(request.headers)
        }

        @Test
        fun `should set null key for NULL type`() {
            val key = createFieldConfig(KafkaFieldType.NULL, "", isKey = true)
            val value = createFieldConfig(KafkaFieldType.STRING, "v")
            val request = client.buildProduceRequest(key, value,emptyList(), "test-topic", -1)

            assertNull(request.key)
            assertNotNull(request.value)
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
    @DisplayName("dispose()")
    inner class DisposeTests {

        @Test
        fun `dispose should stop the client`() {
            client.dispose()
            assertFalse(client.isRunning())
        }
    }
}
