package io.confluent.intellijplugin.producer.client

import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.data.CCloudClusterDataManager
import io.confluent.intellijplugin.data.KafkaDataManager
import io.confluent.intellijplugin.client.KafkaClient
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

@TestApplication
@DisplayName("ProducerClientProvider")
class ProducerClientProviderTest {

    private val onStart: () -> Unit = {}
    private val onStop: () -> Unit = {}

    @Test
    fun `should return KafkaProducerClient for KafkaDataManager`() {
        val mockClient = mock<KafkaClient>()
        val mockDataManager = mock<KafkaDataManager> {
            on { client } doReturn mockClient
        }

        val client = ProducerClientProvider.getClient(mockDataManager, onStart, onStop)
        assertInstanceOf(KafkaProducerClient::class.java, client)
    }

    @Test
    fun `should return CCloudProducerClient for CCloudClusterDataManager`() {
        val mockDataManager = mock<CCloudClusterDataManager>()

        val client = ProducerClientProvider.getClient(mockDataManager, onStart, onStop)
        assertInstanceOf(CCloudProducerClient::class.java, client)
    }
}
