package io.confluent.intellijplugin.telemetry

import com.intellij.testFramework.junit5.TestApplication
import com.segment.analytics.Analytics
import io.confluent.intellijplugin.rfs.KafkaConnectionData
import io.confluent.intellijplugin.settings.app.KafkaPluginSettings
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@TestApplication
class TelemetryTest {

    private lateinit var telemetryService: TelemetryService
    private lateinit var mockAnalytics: Analytics
    private var originalEnableUsageData: Boolean = true

    @BeforeEach
    fun setUp() {
        telemetryService = TelemetryService.getInstance()
        originalEnableUsageData = KafkaPluginSettings.getInstance().enableUsageData
        KafkaPluginSettings.getInstance().enableUsageData = true
        mockAnalytics = mock<Analytics>()
        telemetryService.analytics = mockAnalytics
    }

    @AfterEach
    fun tearDown() {
        // Reset the enableUsageData setting to the original value
        KafkaPluginSettings.getInstance().enableUsageData = originalEnableUsageData
        telemetryService.shutdown()
    }

    @Test
    fun `logUsage delegates to sendTrackEvent`() {
        val event = createTestEvent("TestAction", mapOf("key1" to "value1", "key2" to 42))
        logUsage(event)

        verify(mockAnalytics, times(1)).enqueue(any())
    }

    @Test
    fun `logUser delegates to sendIdentifyEvent`() {
        val traits = mapOf("testKey" to "testValue", "version" to "1.0")
        logUser(traits)

        verify(mockAnalytics, times(1)).enqueue(any())
    }

    @Nested
    @DisplayName("determineAuthMethod")
    inner class DetermineAuthMethod {

        @Test
        fun `detects SASL`() {
            val connectionData = KafkaConnectionData()
            connectionData.secretProperties = "sasl.mechanism=PLAIN"

            assertEquals("SASL", determineAuthMethod(connectionData))
        }

        @Test
        fun `detects SSL`() {
            val connectionData = KafkaConnectionData()
            connectionData.secretProperties = "ssl.keystore.location=/path/to/keystore"

            assertEquals("SSL", determineAuthMethod(connectionData))
        }

        @Test
        fun `detects SASL_SSL`() {
            val connectionData = KafkaConnectionData()
            connectionData.secretProperties = "sasl.mechanism=PLAIN\nssl.keystore.location=/path/to/keystore"

            assertEquals("SASL_SSL", determineAuthMethod(connectionData))
        }

        @Test
        fun `detects Anonymous`() {
            val connectionData = KafkaConnectionData()
            connectionData.secretProperties = ""
            connectionData.anonymous = true

            assertEquals("Anonymous", determineAuthMethod(connectionData))
        }

        @Test
        fun `returns None for no auth`() {
            val connectionData = KafkaConnectionData()
            connectionData.secretProperties = "bootstrap.servers=localhost:9092"

            assertEquals("None", determineAuthMethod(connectionData))
        }
    }

    /**
    * Helper functions
    */

    private fun createTestEvent(
        name: String,
        properties: Map<String, Any> = emptyMap()
    ): TelemetryEvent {
        return object : TelemetryEvent {
            override val eventName = name
            override fun properties() = properties
        }
    }
}
