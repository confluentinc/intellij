package io.confluent.intellijplugin.producer.client

import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.client.KafkaClient
import io.confluent.intellijplugin.common.models.KafkaFieldType
import io.confluent.intellijplugin.consumer.editor.KafkaRecord
import io.confluent.intellijplugin.consumer.models.ConsumerProducerFieldConfig
import io.confluent.intellijplugin.data.KafkaDataManager
import io.confluent.intellijplugin.producer.models.AcksType
import io.confluent.intellijplugin.producer.models.Mode
import io.confluent.intellijplugin.producer.models.ProducerFlowParams
import io.confluent.intellijplugin.producer.models.RecordCompression
import io.confluent.intellijplugin.registry.KafkaRegistryFormat
import io.confluent.intellijplugin.registry.KafkaRegistryType
import io.confluent.intellijplugin.rfs.KafkaConnectionData
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.RecordTooLargeException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.util.Properties
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import org.apache.kafka.common.errors.TimeoutException as KafkaTimeoutException

@TestApplication
class KafkaProducerClientTest {

    private fun createMockDataManager(): KafkaDataManager {
        val mockConnectionData = mock<KafkaConnectionData>()
        val mockClient = mock<KafkaClient> {
            on { connectionData } doReturn mockConnectionData
        }
        return mock<KafkaDataManager> {
            on { client } doReturn mockClient
        }
    }

    private fun createClient(
        dataManager: KafkaDataManager = createMockDataManager(),
        onStart: () -> Unit = {},
        onStop: () -> Unit = {}
    ) = KafkaProducerClient(dataManager, onStart, onStop)

    @Nested
    @DisplayName("Constructor and initial state")
    inner class ConstructorTests {

        @Test
        fun `should initialize with provided callbacks and dataManager`() {
            val dataManager = createMockDataManager()
            var onStartCalled = false
            var onStopCalled = false
            val client = KafkaProducerClient(
                dataManager = dataManager,
                onStart = { onStartCalled = true },
                onStop = { onStopCalled = true }
            )
            assertEquals(dataManager, client.dataManager)
            assertEquals(dataManager.client, client.client)
            assertEquals(dataManager.client.connectionData, client.connectionData)
            assertFalse(onStartCalled, "onStart should not be called during construction")
            assertFalse(onStopCalled, "onStop should not be called during construction")
        }

        @Test
        fun `should initialize with isRunning set to false`() {
            val client = createClient()
            assertFalse(client.isRunning(), "isRunning should be false initially")
        }
    }

    @Nested
    @DisplayName("stop()")
    inner class StopTests {

        @Test
        fun `should set isRunning to false`() {
            val client = createClient()
            client.running.set(true)
            client.stop()
            assertFalse(client.isRunning(), "isRunning should be false after stop")
        }

        @Test
        fun `should not call onStop directly`() {
            var onStopCalled = false
            val client = createClient(onStop = { onStopCalled = true })
            client.running.set(true)
            client.stop()
            assertFalse(onStopCalled, "onStop should not be called by stop() — it is called from start() finally block")
        }
    }

    @Nested
    @DisplayName("dispose()")
    inner class DisposeTests {

        @Test
        fun `should set isRunning to false`() {
            val client = createClient()
            client.running.set(true)
            client.dispose()
            assertFalse(client.isRunning())
        }
    }

    @Nested
    @DisplayName("createProducerProperties()")
    inner class ProducerPropertiesTests {

        private fun clientWithProps(props: Properties): KafkaProducerClient {
            // A client.id keeps createProducerProperties from looking up the plugin version.
            props[ProducerConfig.CLIENT_ID_CONFIG] = "test"
            val connectionData = mock<KafkaConnectionData> {
                on { registryType } doReturn KafkaRegistryType.NONE
            }
            val kafkaClient = mock<KafkaClient> {
                on { this.connectionData } doReturn connectionData
                on { kafkaProps } doReturn props
            }
            return createClient(mock { on { client } doReturn kafkaClient })
        }

        private fun KafkaProducerClient.props() =
            createProducerProperties(RecordCompression.NONE, enableIdempotence = false, AcksType.ALL)

        @Test
        fun `should bound how long send blocks by the send timeout`() {
            val client = clientWithProps(Properties())

            assertEquals(client.sendTimeoutMs.toString(), client.props()[ProducerConfig.MAX_BLOCK_MS_CONFIG])
        }

        @Test
        fun `should keep a max block time set on the connection`() {
            val client = clientWithProps(Properties().apply { this[ProducerConfig.MAX_BLOCK_MS_CONFIG] = "90000" })

            assertEquals("90000", client.props()[ProducerConfig.MAX_BLOCK_MS_CONFIG])
        }
    }

    @Nested
    @DisplayName("produce() send timeouts")
    inner class SendTimeoutTests {

        private val topic = "orders"

        private fun field(text: String, isKey: Boolean) = ConsumerProducerFieldConfig(
            type = KafkaFieldType.STRING,
            valueText = text,
            isKey = isKey,
            topic = topic,
            registryType = KafkaRegistryType.NONE,
            schemaName = "",
            schemaFormat = KafkaRegistryFormat.UNKNOWN,
            parsedSchema = null
        )

        private fun acked(): Future<RecordMetadata> =
            CompletableFuture.completedFuture(RecordMetadata(TopicPartition(topic, 0), 0L, 0, 0L, 1, 1))

        /** A send the broker never acknowledges. */
        private fun hanging(): Future<RecordMetadata> = CompletableFuture()

        private fun producerReturning(vararg futures: Future<RecordMetadata>): KafkaProducer<Any, Any> =
            mock {
                on { send(any<ProducerRecord<Any, Any>>()) } doReturnConsecutively futures.toList()
            }

        private fun runProduce(
            producer: KafkaProducer<Any, Any>,
            flowParams: ProducerFlowParams
        ): List<KafkaRecord> {
            val client = createClient().apply {
                sendTimeoutMs = 10
                running.set(true)
            }
            val updates = mutableListOf<KafkaRecord>()
            client.produce(
                producer, topic, partition = null,
                key = field("k", isKey = true), value = field("v", isKey = false),
                headers = emptyList(), flowParams = flowParams, csvDf = null
            ) { _, records -> updates += records }
            return updates
        }

        private fun autoParams(totalRequests: Int = 0, recordsPerRequest: Int = 1) = ProducerFlowParams(
            mode = Mode.AUTO,
            requestInterval = 0,
            totalRequests = totalRequests,
            flowRecordsCountPerRequest = recordsPerRequest
        )

        @Test
        fun `should report a timed out send as an error row that says the outcome is unknown`() {
            val updates = runProduce(producerReturning(hanging()), ProducerFlowParams(mode = Mode.MANUAL))

            val row = updates.single()
            assertNotNull(row.error)
            assertEquals(topic, row.topic)
            assertTrue(row.errorText.contains("timed out"), row.errorText)
            assertTrue(row.errorText.contains("may or may not have been written"), row.errorText)
        }

        @Test
        fun `should stop auto mode after consecutive send timeouts`() {
            val producer = producerReturning(hanging())

            // produce() returning at all (with an unlimited auto run) is the stop.
            val updates = runProduce(producer, autoParams())

            verify(producer, times(KafkaProducerClient.MAX_CONSECUTIVE_SEND_TIMEOUTS)).send(any<ProducerRecord<Any, Any>>())
            assertTrue(updates.all { it.error != null })
            assertTrue(updates.last().errorText.contains("Auto mode stopped"), updates.last().errorText)
        }

        @Test
        fun `should reset the consecutive timeout counter after a successful send`() {
            // Two timeouts, a success, two timeouts, a success: never three in a row.
            val producer = producerReturning(hanging(), hanging(), acked(), hanging(), hanging(), acked())

            val updates = runProduce(producer, autoParams(totalRequests = 2))

            verify(producer, times(6)).send(any<ProducerRecord<Any, Any>>())
            assertEquals(2, updates.count { it.error == null })
            assertTrue(updates.none { it.errorText.contains("Auto mode stopped") })
        }

        @Test
        fun `should not count timed out sends toward total requests`() {
            val producer = producerReturning(acked(), hanging(), acked(), acked())

            val updates = runProduce(producer, autoParams(totalRequests = 3))

            verify(producer, times(4)).send(any<ProducerRecord<Any, Any>>())
            assertEquals(3, updates.count { it.error == null })
            assertEquals(1, updates.count { it.error != null })
        }

        @Test
        fun `should report a Kafka timeout from send as an error row`() {
            // e.g. no metadata for the topic within max.block.ms because the broker is down
            val failed = CompletableFuture.failedFuture<RecordMetadata>(
                KafkaTimeoutException("Topic $topic not present in metadata after 10 ms.")
            )

            val updates = runProduce(producerReturning(failed), ProducerFlowParams(mode = Mode.MANUAL))

            val row = updates.single()
            assertEquals(topic, row.topic)
            assertTrue(row.errorText.contains("not present in metadata"), row.errorText)
        }

        @Test
        fun `should count Kafka timeouts from send toward the consecutive timeout stop`() {
            val failed = CompletableFuture.failedFuture<RecordMetadata>(KafkaTimeoutException("metadata"))

            val updates = runProduce(producerReturning(failed), autoParams())

            assertEquals(KafkaProducerClient.MAX_CONSECUTIVE_SEND_TIMEOUTS + 1, updates.size)
            assertTrue(updates.last().errorText.contains("Auto mode stopped"), updates.last().errorText)
        }

        @Test
        fun `should still propagate send failures that are not timeouts`() {
            val failed = CompletableFuture.failedFuture<RecordMetadata>(RecordTooLargeException("too large"))

            assertThrows<ExecutionException> {
                runProduce(producerReturning(failed), ProducerFlowParams(mode = Mode.MANUAL))
            }
        }

        @Test
        fun `should not overshoot total requests when a timeout leaves a partial batch`() {
            // Batch 1: ack, timeout (1 produced). Batch 2 must be capped at the 2 remaining records.
            val producer = producerReturning(acked(), hanging(), acked())

            val updates = runProduce(producer, autoParams(totalRequests = 3, recordsPerRequest = 3))

            verify(producer, times(4)).send(any<ProducerRecord<Any, Any>>())
            assertEquals(3, updates.count { it.error == null })
        }
    }
}
