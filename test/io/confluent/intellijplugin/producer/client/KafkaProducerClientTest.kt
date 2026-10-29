package io.confluent.intellijplugin.producer.client

import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.client.KafkaClient
import io.confluent.intellijplugin.common.models.KafkaFieldType
import io.confluent.intellijplugin.consumer.editor.KafkaRecord
import io.confluent.intellijplugin.consumer.models.ConsumerProducerFieldConfig
import io.confluent.intellijplugin.data.KafkaDataManager
import io.confluent.intellijplugin.producer.models.Mode
import io.confluent.intellijplugin.producer.models.ProducerFlowParams
import io.confluent.intellijplugin.registry.KafkaRegistryFormat
import io.confluent.intellijplugin.registry.KafkaRegistryType
import io.confluent.intellijplugin.rfs.KafkaConnectionData
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

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
        ): Pair<KafkaProducerClient, List<KafkaRecord>> {
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
            return client to updates
        }

        private fun autoParams(totalRequests: Int = 0) = ProducerFlowParams(
            mode = Mode.AUTO,
            requestInterval = 0,
            totalRequests = totalRequests
        )

        @Test
        fun `should report a timed out send as an error row that says the record may still be delivered`() {
            val (_, updates) = runProduce(producerReturning(hanging()), ProducerFlowParams(mode = Mode.MANUAL))

            val row = updates.single()
            assertNotNull(row.error)
            assertEquals(topic, row.topic)
            assertTrue(row.errorText.contains("timed out"), row.errorText)
            assertTrue(row.errorText.contains("may still be delivered"), row.errorText)
        }

        @Test
        fun `should stop auto mode after consecutive send timeouts`() {
            val producer = producerReturning(hanging())

            val (client, updates) = runProduce(producer, autoParams())

            verify(producer, times(KafkaProducerClient.MAX_CONSECUTIVE_SEND_TIMEOUTS)).send(any<ProducerRecord<Any, Any>>())
            assertFalse(client.isRunning(), "auto mode should stop itself")
            assertTrue(updates.all { it.error != null })
            assertTrue(updates.last().errorText.contains("Auto mode stopped"), updates.last().errorText)
        }

        @Test
        fun `should reset the consecutive timeout counter after a successful send`() {
            // Two timeouts, a success, two timeouts, a success: never three in a row.
            val producer = producerReturning(hanging(), hanging(), acked(), hanging(), hanging(), acked())

            val (_, updates) = runProduce(producer, autoParams(totalRequests = 2))

            verify(producer, times(6)).send(any<ProducerRecord<Any, Any>>())
            assertEquals(2, updates.count { it.error == null })
            assertTrue(updates.none { it.errorText.contains("Auto mode stopped") })
        }

        @Test
        fun `should not count timed out sends toward total requests`() {
            val producer = producerReturning(acked(), hanging(), acked(), acked())

            val (_, updates) = runProduce(producer, autoParams(totalRequests = 3))

            verify(producer, times(4)).send(any<ProducerRecord<Any, Any>>())
            assertEquals(3, updates.count { it.error == null })
            assertEquals(1, updates.count { it.error != null })
        }
    }
}
