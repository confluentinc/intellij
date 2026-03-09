package io.confluent.intellijplugin.producer.client

import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.client.KafkaClient
import io.confluent.intellijplugin.rfs.KafkaConnectionData
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.concurrent.atomic.AtomicBoolean

@TestApplication
class KafkaProducerClientTest {

    private fun createMockClient(): KafkaClient {
        val mockConnectionData = mock<KafkaConnectionData>()
        return mock<KafkaClient> {
            on { connectionData } doReturn mockConnectionData
        }
    }

    @Nested
    @DisplayName("Lifecycle management")
    inner class LifecycleTests {

        @Test
        fun `stop should set isRunning to false`() {
            val client = KafkaProducerClient(createMockClient())
            client.isRunning.set(true)
            client.stop()
            assertFalse(client.isRunning(), "isRunning should be false after stop")
        }

        @Test
        fun `stop should only be effective once when called multiple times`() {
            var guardPassedCount = 0
            val mockIsRunning = mock<AtomicBoolean> {
                on { getAndSet(false) } doAnswer {
                    val wasRunning = guardPassedCount == 0
                    if (wasRunning) guardPassedCount++
                    wasRunning
                }
            }

            val client = KafkaProducerClient(createMockClient())
            val field = KafkaProducerClient::class.java.getDeclaredField("isRunning")
            field.isAccessible = true
            field.set(client, mockIsRunning)

            client.stop()
            client.stop()
            client.stop()

            assertEquals(1, guardPassedCount, "stop should only be effective once")
        }
    }
}
