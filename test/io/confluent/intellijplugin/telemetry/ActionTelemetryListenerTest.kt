package io.confluent.intellijplugin.telemetry

import com.intellij.openapi.actionSystem.*
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.TestActionEvent
import com.segment.analytics.Analytics
import io.confluent.intellijplugin.settings.app.KafkaPluginSettings
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.never

@TestApplication
class ActionTelemetryListenerTest {

    private lateinit var listener: ActionTelemetryListener
    private lateinit var telemetryService: TelemetryService
    private lateinit var mockAnalytics: Analytics
    private var originalEnableUsageData: Boolean = true

    @BeforeEach
    fun setUp() {
        listener = ActionTelemetryListener()
        telemetryService = TelemetryService.getInstance()
        originalEnableUsageData = KafkaPluginSettings.getInstance().enableUsageData
        KafkaPluginSettings.getInstance().enableUsageData = true
        mockAnalytics = mock(Analytics::class.java)
        telemetryService.analytics = mockAnalytics
    }

    @AfterEach
    fun tearDown() {
        KafkaPluginSettings.getInstance().enableUsageData = originalEnableUsageData
        telemetryService.shutdown()
    }

    @Nested
    @DisplayName("afterActionPerformed")
    inner class AfterActionPerformed {

        @Test
        fun `tracks Kafka actions by known class name and sends telemetry`() {
            // Use known inner class name from Kafka plugin
            val action = CreateNewConnectionAction()
            val event = TestActionEvent.createTestEvent(action, DataContext.EMPTY_CONTEXT)
            val result = mock(AnActionResult::class.java)

            listener.afterActionPerformed(action, event, result)

            verify(mockAnalytics, times(1)).enqueue(any())
        }

        @Test
        fun `ignores non-Kafka actions and does not send telemetry`() {
            val action = NonKafkaAction()
            val event = TestActionEvent.createTestEvent(action, DataContext.EMPTY_CONTEXT)
            val result = mock(AnActionResult::class.java)
            listener.afterActionPerformed(action, event, result)

            verify(mockAnalytics, never()).enqueue(any())
        }

        @Test
        fun `does not send telemetry when disabled`() {
            KafkaPluginSettings.getInstance().enableUsageData = false
            val action = CreateNewConnectionAction()
            val event = TestActionEvent.createTestEvent(action, DataContext.EMPTY_CONTEXT)
            val result = mock(AnActionResult::class.java)
            listener.afterActionPerformed(action, event, result)

            verify(mockAnalytics, never()).enqueue(any())
        }

        @Test
        fun `tracks multiple Kafka action tiggers`() {
            val action = CreateNewConnectionAction()
            val result = mock(AnActionResult::class.java)
            repeat(3) {
                val event = TestActionEvent.createTestEvent(action, DataContext.EMPTY_CONTEXT)
                listener.afterActionPerformed(action, event, result)
            }

            verify(mockAnalytics, times(3)).enqueue(any())
        }
    }

    @Test
    fun `normalizeActionName removes kafka prefix and converts to PascalCase`() {
        assertEquals("CreateProducer", listener.normalizeActionName("kafka.create.producer"))
        assertEquals("ExportToCsv", listener.normalizeActionName("Kafka.Export.ToCsv"))
        assertEquals("DeleteTopic", listener.normalizeActionName("kafka.DeleteTopicAction"))
    }

    @Nested
    @DisplayName("isKafkaAction")
    inner class IsKafkaAction {

        @Test
        fun `detects Kafka actions`() {
            assertTrue(listener.isKafkaAction("kafka.createTopic"))
            assertTrue(listener.isKafkaAction("Kafka.CreateTopic"))
            assertTrue(listener.isKafkaAction("kafka.delete.topic"))
            assertTrue(listener.isKafkaAction("Kafka.Export.ToCsv"))
        }

        @Test
        fun `ignores non-Kafka actions`() {
            assertFalse(listener.isKafkaAction("intellij.someAction"))
            assertFalse(listener.isKafkaAction("EditSource"))
            assertFalse(listener.isKafkaAction(""))
        }
    }

    @Nested
    @DisplayName("isKafkaActionClass")
    inner class IsKafkaActionClass {

        @Test
        fun `detects known inner classes`() {
            assertTrue(listener.isKafkaActionClass("CreateNewConnectionAction"))
            assertTrue(listener.isKafkaActionClass("MonitoringSettingsAction"))
            assertTrue(listener.isKafkaActionClass("AddConnectionAction"))
            assertTrue(listener.isKafkaActionClass("RemoveConnectionAction"))
            assertTrue(listener.isKafkaActionClass("DuplicateConnectionAction"))
        }

        @Test
        fun `ignores unknown classes`() {
            assertFalse(listener.isKafkaActionClass("UnknownAction"))
            assertFalse(listener.isKafkaActionClass("RandomClass"))
            assertFalse(listener.isKafkaActionClass(""))
        }
    }


    /**
    * Helper functions
    */

    // Create a test action that matches a known Kafka plugin recognized inner class name
    private class CreateNewConnectionAction : AnAction() {
        override fun actionPerformed(e: AnActionEvent) {}
    }

    // Create a test action with non-Kafka plugin recognized name
    private class NonKafkaAction : AnAction() {
        override fun actionPerformed(e: AnActionEvent) {}
    }
}
