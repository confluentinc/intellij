package io.confluent.intellijplugin.telemetry

import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions
import org.mockito.kotlin.*

/**
 * Tests for KafkaErrorReportSubmitter error filtering using throwableText.
 */
@TestApplication
class KafkaErrorReportSubmitterTest {

    @Nested
    @DisplayName("Error filtering")
    inner class ErrorFilteringTests {

        @Test
        fun `should identify plugin error from throwableText`() {
            val submitter = KafkaErrorReportSubmitter()
            val event = createEventWithThrowableText("""
                java.lang.RuntimeException: Test error
                    at io.confluent.intellijplugin.consumer.ConsumerService.consume(ConsumerService.kt:42)
                    at org.apache.kafka.clients.consumer.KafkaConsumer.poll(KafkaConsumer.java:123)
            """.trimIndent())

            val isPluginError = submitter.isPluginRelatedError(event)

            Assertions.assertTrue(isPluginError, "Should identify plugin errors")
        }

        @Test
        fun `should reject non-plugin errors`() {
            val submitter = KafkaErrorReportSubmitter()
            val event = createEventWithThrowableText("""
                java.lang.RuntimeException: Platform error
                    at com.intellij.ui.BalloonImpl.show(BalloonImpl.java:100)
                    at com.intellij.openapi.ui.popup.Balloon.show(Balloon.java:50)
            """.trimIndent())

            val isPluginError = submitter.isPluginRelatedError(event)

            Assertions.assertFalse(isPluginError, "Should reject non-plugin errors")
        }

        @Test
        fun `should identify plugin code deep in stack trace`() {
            val submitter = KafkaErrorReportSubmitter()
            val event = createEventWithThrowableText("""
                java.io.IOException: Network error
                    at okhttp3.internal.connection.RealConnection.connect(Connection.java:100)
                    at io.confluent.intellijplugin.registry.SchemaRegistryClient.fetchSchema(Client.kt:75)
                    at com.intellij.util.net.HttpRequests.request(HttpRequests.java:200)
            """.trimIndent())

            val isPluginError = submitter.isPluginRelatedError(event)

            Assertions.assertTrue(isPluginError, "Should identify plugin code anywhere in stack")
        }

        @Test
        fun `should handle TextBasedThrowable edge case`() {
            val submitter = KafkaErrorReportSubmitter()
            // TextBasedThrowable scenario: throwable is null but throwableText has content
            val event = mock<IdeaLoggingEvent> {
                on { throwable } doReturn null
                on { throwableText } doReturn """
                    java.lang.RuntimeException: Error
                        at io.confluent.intellijplugin.core.Connection.connect(Connection.kt:50)
                """.trimIndent()
            }

            val isPluginError = submitter.isPluginRelatedError(event)

            Assertions.assertTrue(isPluginError, "Should work with TextBasedThrowable")
        }

        @Test
        fun `should reject empty throwableText`() {
            val submitter = KafkaErrorReportSubmitter()
            val event = mock<IdeaLoggingEvent> {
                on { throwableText } doReturn ""
            }

            val isPluginError = submitter.isPluginRelatedError(event)

            Assertions.assertFalse(isPluginError, "Should reject empty throwableText")
        }
    }

    /**
     * Helper function to create a mock IdeaLoggingEvent with throwableText
     */
    private fun createEventWithThrowableText(throwableText: String): IdeaLoggingEvent {
        return mock {
            on { getThrowableText() } doReturn throwableText
        }
    }
}
