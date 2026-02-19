package io.confluent.intellijplugin.telemetry

import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.SubmittedReportInfo
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.Consumer
import org.junit.jupiter.api.*
import org.mockito.kotlin.*
import java.awt.Component

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
        fun `should reject empty throwableText`() {
            val submitter = KafkaErrorReportSubmitter()
            val event = mock<IdeaLoggingEvent> {
                on { throwableText } doReturn ""
            }

            val isPluginError = submitter.isPluginRelatedError(event)

            Assertions.assertFalse(isPluginError, "Should reject empty throwableText")
        }
    }

    @Nested
    @DisplayName("Submit behavior")
    inner class SubmitBehaviorTests {

        private val originalTestDialog = TestDialogManager.getTestImplementation()

        @BeforeEach
        fun setup() {
            // Configure test dialog to auto-accept all dialogs
            TestDialogManager.setTestDialog(TestDialog.OK)
        }

        @AfterEach
        fun teardown() {
            // Restore original test dialog implementation
            TestDialogManager.setTestDialog(originalTestDialog)
        }

        @Test
        fun `should reject submission when only non-plugin errors provided`() {
            val submitter = KafkaErrorReportSubmitter()
            val consumer = mock<Consumer<SubmittedReportInfo>>()
            val parentComponent = mock<Component>()

            // Create events with only non-plugin stack traces
            val nonPluginEvent = createEventWithThrowableText("""
                java.lang.RuntimeException: Platform error
                    at com.intellij.ui.BalloonImpl.show(BalloonImpl.java:100)
                    at com.intellij.openapi.ui.popup.Balloon.show(Balloon.java:50)
            """.trimIndent())

            val result = submitter.submit(
                arrayOf(nonPluginEvent),
                additionalInfo = null,
                parentComponent = parentComponent,
                consumer = consumer
            )

            Assertions.assertFalse(result, "Should return false to delegate to JetBrains reporter")
            verifyNoInteractions(consumer)
        }

        @Test
        fun `should accept submission when plugin errors provided`() {
            val submitter = KafkaErrorReportSubmitter()
            val consumer = mock<Consumer<SubmittedReportInfo>>()
            val parentComponent = mock<Component>()

            // Create event with plugin stack trace
            val pluginEvent = createEventWithThrowableText("""
                java.lang.RuntimeException: Plugin error
                    at io.confluent.intellijplugin.consumer.ConsumerService.consume(ConsumerService.kt:42)
                    at org.apache.kafka.clients.consumer.KafkaConsumer.poll(KafkaConsumer.java:123)
            """.trimIndent())

            val result = submitter.submit(
                arrayOf(pluginEvent),
                additionalInfo = null,
                parentComponent = parentComponent,
                consumer = consumer
            )

            Assertions.assertTrue(result, "Should return true to handle plugin errors")
        }

        @Test
        fun `should filter mixed events and accept only plugin errors`() {
            val submitter = KafkaErrorReportSubmitter()
            val consumer = mock<Consumer<SubmittedReportInfo>>()
            val parentComponent = mock<Component>()

            val nonPluginEvent = createEventWithThrowableText("""
                java.lang.RuntimeException: Platform error
                    at com.intellij.ui.BalloonImpl.show(BalloonImpl.java:100)
            """.trimIndent())

            val pluginEvent = createEventWithThrowableText("""
                java.lang.RuntimeException: Plugin error
                    at io.confluent.intellijplugin.registry.SchemaRegistryClient.fetchSchema(Client.kt:75)
            """.trimIndent())

            val result = submitter.submit(
                arrayOf(nonPluginEvent, pluginEvent),
                additionalInfo = null,
                parentComponent = parentComponent,
                consumer = consumer
            )

            Assertions.assertTrue(result, "Should return true when at least one plugin error exists")
        }
    }

    @Nested
    @DisplayName("UI text")
    inner class UITextTests {

        @Test
        fun `should return correct report action text`() {
            val submitter = KafkaErrorReportSubmitter()

            val text = submitter.getReportActionText()

            Assertions.assertEquals("Report to plugin vendor (Confluent, Inc.)", text)
        }

        @Test
        fun `should return privacy notice text`() {
            val submitter = KafkaErrorReportSubmitter()

            val text = submitter.getPrivacyNoticeText()

            Assertions.assertEquals(
                "Error reports help improve the Kafka plugin. No personal data is collected.",
                text
            )
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
