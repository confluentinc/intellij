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
            val dataManager = createMockDataManager()
            val client = KafkaProducerClient(
                dataManager = dataManager,
                onStart = {},
                onStop = {}
            )
            assertFalse(client.isRunning(), "isRunning should be false initially")
        }
    }

    @Nested
    @DisplayName("Lifecycle management")
    inner class LifecycleTests {

        @Test
        fun `stop should set isRunning to false and invoke onStop callback`() {
            val dataManager = createMockDataManager()
            var onStopCalled = false
            val client = KafkaProducerClient(
                dataManager = dataManager,
                onStart = {},
                onStop = { onStopCalled = true }
            )
            client.isRunning.set(true)
            client.stop()
            assertFalse(client.isRunning(), "isRunning should be false after stop")
            assertTrue(onStopCalled, "onStop callback should be invoked")
        }

        @Test
        fun `stop should only invoke onStop once when called multiple times`() {
            val dataManager = createMockDataManager()
            var onStopCallCount = 0
            val client = KafkaProducerClient(
                dataManager = dataManager,
                onStart = {},
                onStop = { onStopCallCount++ }
            )
            client.isRunning.set(true)
            client.stop()
            client.stop()
            client.stop()
            assertFalse(client.isRunning(), "isRunning should remain false")
            assertEquals(1, onStopCallCount, "onStop should only be called once")
        }

        @Test
        fun `stop should not invoke onStop when not running`() {
            val dataManager = createMockDataManager()
            var onStopCalled = false
            val client = KafkaProducerClient(
                dataManager = dataManager,
                onStart = {},
                onStop = { onStopCalled = true }
            )
            client.stop()
            assertFalse(client.isRunning(), "isRunning should be false")
            assertFalse(onStopCalled, "onStop should not be invoked when not running")
        }
    }
}
