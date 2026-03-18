package io.confluent.intellijplugin.producer.client

import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.client.KafkaClient
import io.confluent.intellijplugin.data.KafkaDataManager
import io.confluent.intellijplugin.rfs.KafkaConnectionData
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

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
}
