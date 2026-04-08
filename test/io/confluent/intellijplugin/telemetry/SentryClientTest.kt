package io.confluent.intellijplugin.telemetry

import com.intellij.testFramework.junit5.TestApplication
import io.sentry.SentryEvent
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for SentryClient error filtering.
 * Verifies that only plugin-related exceptions are sent to Sentry.
 */
@TestApplication
class SentryClientTest {

    @Nested
    @DisplayName("Plugin error filtering")
    inner class ErrorFilteringTests {

        @Test
        fun `should identify plugin error from stack trace`() {
            val pluginException = RuntimeException("Test plugin error").apply {
                stackTrace = arrayOf(
                    StackTraceElement(
                        "io.confluent.intellijplugin.consumer.ConsumerService",
                        "consume",
                        "ConsumerService.kt",
                        42
                    ),
                    StackTraceElement(
                        "org.apache.kafka.clients.consumer.KafkaConsumer",
                        "poll",
                        "KafkaConsumer.java",
                        123
                    )
                )
            }

            val event = SentryEvent(pluginException)
            val isPluginError = SentryClient.isPluginRelatedError(event)

            assertTrue(isPluginError, "Should identify errors with plugin package in stack trace")
        }

        @Test
        fun `should reject platform-only errors`() {
            val platformException = RuntimeException("Platform error").apply {
                stackTrace = arrayOf(
                    StackTraceElement(
                        "com.intellij.ui.BalloonImpl",
                        "show",
                        "BalloonImpl.java",
                        100
                    ),
                    StackTraceElement(
                        "com.intellij.openapi.ui.popup.Balloon",
                        "show",
                        "Balloon.java",
                        50
                    )
                )
            }

            val event = SentryEvent(platformException)
            val isPluginError = SentryClient.isPluginRelatedError(event)

            assertFalse(isPluginError, "Should reject platform-only errors")
        }

        @Test
        fun `should reject other plugin errors`() {
            val otherPluginException = RuntimeException("Other plugin error").apply {
                stackTrace = arrayOf(
                    StackTraceElement(
                        "com.intellij.kubernetes.KubernetesClient",
                        "connect",
                        "KubernetesClient.java",
                        150
                    ),
                    StackTraceElement(
                        "com.intellij.openapi.application.ApplicationManager",
                        "getApplication",
                        "ApplicationManager.java",
                        80
                    )
                )
            }

            val event = SentryEvent(otherPluginException)
            val isPluginError = SentryClient.isPluginRelatedError(event)

            assertFalse(isPluginError, "Should reject errors from other plugins")
        }

        @Test
        fun `should identify plugin code deep in stack trace`() {
            val deepPluginException = RuntimeException("Deep plugin error").apply {
                stackTrace = arrayOf(
                    StackTraceElement(
                        "okhttp3.internal.connection.RealConnection",
                        "connect",
                        "RealConnection.java",
                        200
                    ),
                    StackTraceElement(
                        "io.confluent.intellijplugin.registry.SchemaRegistryClient",
                        "fetchSchema",
                        "SchemaRegistryClient.kt",
                        75
                    ),
                    StackTraceElement(
                        "com.intellij.util.net.HttpRequests",
                        "request",
                        "HttpRequests.java",
                        300
                    )
                )
            }

            val event = SentryEvent(deepPluginException)
            val isPluginError = SentryClient.isPluginRelatedError(event)

            assertTrue(isPluginError, "Should find plugin code anywhere in stack trace")
        }

        @Test
        fun `should handle events without throwable`() {
            val event = SentryEvent()  // No throwable attached

            val isPluginError = SentryClient.isPluginRelatedError(event)

            assertFalse(isPluginError, "Should reject events without throwable")
        }

        @Test
        fun `should handle empty stack trace`() {
            val emptyStackException = RuntimeException("Empty stack").apply {
                stackTrace = arrayOf()
            }

            val event = SentryEvent(emptyStackException)
            val isPluginError = SentryClient.isPluginRelatedError(event)

            assertFalse(isPluginError, "Should reject exceptions with empty stack trace")
        }

        @Test
        fun `should identify plugin subpackages`() {
            val subpackageException = RuntimeException("Subpackage error").apply {
                stackTrace = arrayOf(
                    StackTraceElement(
                        "io.confluent.intellijplugin.core.settings.LocalConnectionSettings",
                        "save",
                        "LocalConnectionSettings.kt",
                        120
                    )
                )
            }

            val event = SentryEvent(subpackageException)
            val isPluginError = SentryClient.isPluginRelatedError(event)

            assertTrue(isPluginError, "Should identify errors from plugin subpackages")
        }

        @Test
        fun `should reject similar package names`() {
            val similarPackageException = RuntimeException("Similar package").apply {
                stackTrace = arrayOf(
                    StackTraceElement(
                        "io.confluent.kafka.serializers.KafkaAvroSerializer",
                        "serialize",
                        "KafkaAvroSerializer.java",
                        50
                    )
                )
            }

            val event = SentryEvent(similarPackageException)
            val isPluginError = SentryClient.isPluginRelatedError(event)

            assertFalse(
                isPluginError,
                "Should reject packages that start with io.confluent but aren't the plugin"
            )
        }
    }
}
