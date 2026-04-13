package io.confluent.intellijplugin.telemetry

import com.intellij.testFramework.junit5.TestApplication
import io.sentry.SentryEvent
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for SentryErrorFilter.
 * Verifies that only Confluent-related exceptions are sent to Sentry.
 */
@TestApplication
class SentryClientTest {

    @Nested
    @DisplayName("Confluent error filtering")
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
            val isPluginError = SentryErrorFilter.isPluginRelatedError(event)

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
            val isPluginError = SentryErrorFilter.isPluginRelatedError(event)

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
            val isPluginError = SentryErrorFilter.isPluginRelatedError(event)

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
            val isPluginError = SentryErrorFilter.isPluginRelatedError(event)

            assertTrue(isPluginError, "Should find plugin code anywhere in stack trace")
        }

        @Test
        fun `should handle events without throwable`() {
            val event = SentryEvent()  // No throwable attached

            val isPluginError = SentryErrorFilter.isPluginRelatedError(event)

            assertFalse(isPluginError, "Should reject events without throwable")
        }

        @Test
        fun `should handle empty stack trace`() {
            val emptyStackException = RuntimeException("Empty stack").apply {
                stackTrace = arrayOf()
            }

            val event = SentryEvent(emptyStackException)
            val isPluginError = SentryErrorFilter.isPluginRelatedError(event)

            assertFalse(isPluginError, "Should reject exceptions with empty stack trace")
        }

        @Test
        fun `should accept Confluent library errors`() {
            val confluentLibraryException = RuntimeException("Confluent library error").apply {
                stackTrace = arrayOf(
                    StackTraceElement(
                        "io.confluent.kafka.serializers.KafkaAvroSerializer",
                        "serialize",
                        "KafkaAvroSerializer.java",
                        50
                    )
                )
            }

            val event = SentryEvent(confluentLibraryException)
            val isPluginError = SentryErrorFilter.isPluginRelatedError(event)

            assertTrue(
                isPluginError,
                "Should accept errors from any io.confluent package"
            )
        }

        @Test
        fun `should identify plugin error in cause chain`() {
            val pluginException = RuntimeException("Plugin error").apply {
                stackTrace = arrayOf(
                    StackTraceElement(
                        "io.confluent.intellijplugin.consumer.ConsumerService",
                        "consume",
                        "ConsumerService.kt",
                        42
                    )
                )
            }

            val wrappingException = RuntimeException("Wrapped by platform", pluginException).apply {
                stackTrace = arrayOf(
                    StackTraceElement(
                        "com.intellij.openapi.application.ApplicationManager",
                        "run",
                        "ApplicationManager.java",
                        100
                    )
                )
            }

            val event = SentryEvent(wrappingException)
            val isPluginError = SentryErrorFilter.isPluginRelatedError(event)

            assertTrue(isPluginError, "Should detect plugin error in cause chain")
        }

        @Test
        fun `should identify plugin error in suppressed exceptions`() {
            val pluginException = RuntimeException("Plugin error").apply {
                stackTrace = arrayOf(
                    StackTraceElement(
                        "io.confluent.intellijplugin.registry.SchemaRegistryClient",
                        "fetch",
                        "SchemaRegistryClient.kt",
                        50
                    )
                )
            }

            val mainException = RuntimeException("Main exception").apply {
                stackTrace = arrayOf(
                    StackTraceElement(
                        "com.intellij.util.net.HttpRequests",
                        "request",
                        "HttpRequests.java",
                        200
                    )
                )
                addSuppressed(pluginException)
            }

            val event = SentryEvent(mainException)
            val isPluginError = SentryErrorFilter.isPluginRelatedError(event)

            assertTrue(isPluginError, "Should detect plugin error in suppressed exceptions")
        }

        @Test
        fun `should handle deep cause chain without Confluent code`() {
            val innerException = RuntimeException("Inner").apply {
                stackTrace = arrayOf(
                    StackTraceElement("java.lang.Thread", "run", "Thread.java", 100)
                )
            }

            val middleException = RuntimeException("Middle", innerException).apply {
                stackTrace = arrayOf(
                    StackTraceElement("com.intellij.openapi.util.Computable", "compute", "Computable.java", 50)
                )
            }

            val outerException = RuntimeException("Outer", middleException).apply {
                stackTrace = arrayOf(
                    StackTraceElement("com.intellij.openapi.application.Application", "run", "Application.java", 200)
                )
            }

            val event = SentryEvent(outerException)
            val isPluginError = SentryErrorFilter.isPluginRelatedError(event)

            assertFalse(isPluginError, "Should reject deep cause chain without Confluent code")
        }

        @Test
        fun `should identify Confluent error from exception message`() {
            val platformException = RuntimeException(
                "Cannot check provider io.confluent.intellijplugin.common.editor.KafkaEditorProvider"
            ).apply {
                stackTrace = arrayOf(
                    StackTraceElement(
                        "com.intellij.openapi.fileEditor.impl.FileEditorProviderManagerImpl",
                        "getProviders",
                        "FileEditorProviderManagerImpl.kt",
                        156
                    ),
                    StackTraceElement(
                        "kotlinx.coroutines.internal.ScopeCoroutine",
                        "afterResume",
                        "Scopes.kt",
                        36
                    )
                )
            }

            val event = SentryEvent(platformException)
            val isPluginError = SentryErrorFilter.isPluginRelatedError(event)

            assertTrue(
                isPluginError,
                "Should identify Confluent errors when message references Confluent package"
            )
        }
    }
}
