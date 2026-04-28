package io.confluent.intellijplugin.core.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ExceptionUtils")
class ExceptionUtilsTest {

    @Nested
    @DisplayName("realSource")
    inner class RealSource {

        @Test
        fun `should return self when message is present`() {
            val ex = RuntimeException("boom")
            assertSame(ex, ex.realSource())
        }

        @Test
        fun `should walk causes until a message is found`() {
            val rootCause = IllegalStateException("real reason")
            val middle = RuntimeException(null as String?, rootCause)
            val outer = RuntimeException(null as String?, middle)

            assertSame(rootCause, outer.realSource())
        }

        @Test
        fun `should stop walking when no further cause is available`() {
            val outer = RuntimeException(null as String?)
            assertSame(outer, outer.realSource())
        }

        @Test
        fun `should terminate on cause cycle without infinite loop`() {
            // Build a -> b -> a cycle by overriding the cause getter (initCause forbids self-loops)
            val a = RuntimeException(null as String?)
            val b = object : RuntimeException(null as String?) {
                override val cause: Throwable get() = a
            }
            a.initCause(b)

            // Should return without StackOverflowError
            val result = a.realSource()
            assertEquals(null, result.message)
        }
    }

    @Nested
    @DisplayName("messageOrDefault")
    inner class MessageOrDefault {

        @Test
        fun `should return localizedMessage when present`() {
            val ex = RuntimeException("hello")
            assertEquals("hello", ex.messageOrDefault())
        }

        @Test
        fun `should return root cause message when outer message is null`() {
            val rootCause = IllegalStateException("root reason")
            val outer = RuntimeException(null as String?, rootCause)
            assertEquals("root reason", outer.messageOrDefault())
        }

        @Test
        fun `should return provided default when no message is present anywhere`() {
            val ex = RuntimeException(null as String?)
            assertEquals("fallback", ex.messageOrDefault("fallback"))
        }

        @Test
        fun `should return empty string when no message and no default`() {
            val ex = RuntimeException(null as String?)
            assertEquals("", ex.messageOrDefault())
        }
    }
}
