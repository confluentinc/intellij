package io.confluent.intellijplugin.telemetry

import com.intellij.testFramework.junit5.TestApplication
import com.segment.analytics.Analytics
import io.confluent.intellijplugin.settings.app.KafkaPluginSettings
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import org.mockito.kotlin.any

@TestApplication
class TelemetryServiceTest {

    private lateinit var telemetryService: TelemetryService
    private lateinit var mockAnalytics: Analytics
    private var originalEnableUsageData: Boolean = true

    @BeforeEach
    fun setUp() {
        telemetryService = TelemetryService.getInstance()
        originalEnableUsageData = KafkaPluginSettings.getInstance().enableUsageData
        mockAnalytics = mock(Analytics::class.java)
    }

    @AfterEach
    fun tearDown() {
        // Reset the enableUsageData setting to the original value
        KafkaPluginSettings.getInstance().enableUsageData = originalEnableUsageData
        telemetryService.shutdown()
    }

    @Nested
    @DisplayName("sendTrackEvent")
    inner class SendTrackEvent {

        @BeforeEach
        fun setUpTrackEvent() {
            telemetryService.analytics = mockAnalytics
            KafkaPluginSettings.getInstance().enableUsageData = true
        }

        @Test
        fun `respects user opt-out`() {
            KafkaPluginSettings.getInstance().enableUsageData = false
            telemetryService.sendTrackEvent("TestEvent", mapOf("key" to "value"))

            verify(mockAnalytics, never()).enqueue(any())
        }

        @Test
        fun `sends events when user opted in`() {
            telemetryService.sendTrackEvent("TestEvent", mapOf("key" to "value"))

            verify(mockAnalytics, times(1)).enqueue(any())
        }

        @Test
        fun `handles exceptions during event tracking`() {
            doThrow(RuntimeException("Test exception")).`when`(mockAnalytics).enqueue(any())

            assertDoesNotThrow {
                telemetryService.sendTrackEvent("TestEvent", mapOf("key" to "value"))
            }
        }

        @Test
        fun `handles uninitialized analytics during event tracking`() {
            telemetryService.analytics = null

            assertDoesNotThrow {
                telemetryService.sendTrackEvent("TestEvent", mapOf("key" to "value"))
            }
        }

        @Test
        fun `sends multiple events`() {
            telemetryService.sendTrackEvent("Event1", mapOf("k1" to "v1"))
            telemetryService.sendTrackEvent("Event2", mapOf("k2" to "v2"))
            telemetryService.sendTrackEvent("Event3", mapOf("k3" to "v3"))

            verify(mockAnalytics, times(3)).enqueue(any())
        }
    }

    @Nested
    @DisplayName("sendIdentifyEvent")
    inner class SendIdentifyEvent {

        @BeforeEach
        fun setUpIdentifyEvent() {
            telemetryService.analytics = mockAnalytics
            KafkaPluginSettings.getInstance().enableUsageData = true
        }

        @Test
        fun `respects user opt-out`() {
            KafkaPluginSettings.getInstance().enableUsageData = false
            telemetryService.sendIdentifyEvent(mapOf("trait" to "value"))

            verify(mockAnalytics, never()).enqueue(any())
        }

        @Test
        fun `sends events when user opted in`() {
            telemetryService.sendIdentifyEvent(mapOf("trait" to "value"))

            verify(mockAnalytics, times(1)).enqueue(any())
        }

        @Test
        fun `handles exceptions during identify event tracking`() {
            doThrow(RuntimeException("Test exception")).`when`(mockAnalytics).enqueue(any())

            assertDoesNotThrow {
                telemetryService.sendIdentifyEvent(mapOf("trait" to "value"))
            }
        }

        @Test
        fun `handles uninitialized analytics during identify event tracking`() {
            telemetryService.analytics = null

            assertDoesNotThrow {
                telemetryService.sendIdentifyEvent(mapOf("trait" to "value"))
            }
        }

        @Test
        fun `sends multiple events`() {
            telemetryService.sendIdentifyEvent(mapOf("trait1" to "value1"))
            telemetryService.sendIdentifyEvent(mapOf("trait2" to "value2"))
            telemetryService.sendIdentifyEvent(mapOf("trait3" to "value3"))

            verify(mockAnalytics, times(3)).enqueue(any())
        }
    }

    @Test
    fun `shutdown calls analytics flush and shutdown`() {
        telemetryService.analytics = mockAnalytics
        telemetryService.shutdown()

        verify(mockAnalytics, times(1)).flush()
        verify(mockAnalytics, times(1)).shutdown()
    }
}
