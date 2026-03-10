package io.confluent.intellijplugin.producer.client

import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.data.CCloudClusterDataManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

@TestApplication
class CCloudProducerClientTest {

    private fun createClient(
        onStart: () -> Unit = {},
        onStop: () -> Unit = {}
    ): CCloudProducerClient {
        val mockDataManager = mock<CCloudClusterDataManager>()
        return CCloudProducerClient(
            clusterDataManager = mockDataManager,
            onStart = onStart,
            onStop = onStop
        )
    }

    @Nested
    @DisplayName("Initial state")
    inner class InitialStateTests {

        @Test
        fun `should not be running initially`() {
            val client = createClient()
            assertFalse(client.isRunning())
        }
    }

    @Nested
    @DisplayName("stop()")
    inner class StopTests {

        @Test
        fun `stop should set running to false`() {
            val client = createClient()
            client.stop()
            assertFalse(client.isRunning())
        }
    }

    @Nested
    @DisplayName("dispose()")
    inner class DisposeTests {

        @Test
        fun `dispose should stop the client`() {
            val client = createClient()
            client.dispose()
            assertFalse(client.isRunning())
        }
    }
}
