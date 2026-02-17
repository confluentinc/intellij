package io.confluent.intellijplugin.core.settings.connections

import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.core.settings.connections.CCloudDisplayGroup.Companion.formatSessionExpiry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

@TestApplication
class CCloudDisplayGroupTest {

    @Nested
    @DisplayName("formatSessionExpiry")
    inner class FormatSessionExpiry {

        @Test
        fun `should return empty string when endOfLifetime is null`() {
            assertEquals("", formatSessionExpiry(null))
        }

        @Test
        fun `should return expired message when endOfLifetime is in the past`() {
            val pastInstant = Instant.now().minusSeconds(60)

            val result = formatSessionExpiry(pastInstant)

            assertTrue(result.contains("expired", ignoreCase = true), "Expected expired message but got: $result")
        }

        @Test
        fun `should show hours and minutes when both are present`() {
            val futureInstant = Instant.now().plusSeconds(2 * 3600 + 30 * 60 + 30)

            val result = formatSessionExpiry(futureInstant)

            assertTrue(result.startsWith("in 2h 30m"), "Expected relative time 'in 2h 30m' but got: $result")
            assertTrue(result.contains("("), "Expected absolute time in parentheses but got: $result")
        }

        @Test
        fun `should show only hours when minutes are zero`() {
            val futureInstant = Instant.now().plusSeconds(3 * 3600 + 30)

            val result = formatSessionExpiry(futureInstant)

            assertTrue(result.startsWith("in 3h"), "Expected relative time 'in 3h' but got: $result")
            assertTrue(!result.startsWith("in 3h 0m"), "Should not show '0m' but got: $result")
        }

        @Test
        fun `should show only minutes when hours are zero`() {
            val futureInstant = Instant.now().plusSeconds(45 * 60 + 30)

            val result = formatSessionExpiry(futureInstant)

            assertTrue(result.startsWith("in 45m"), "Expected relative time 'in 45m' but got: $result")
        }

        @Test
        fun `should show less than one minute when under 60 seconds remain`() {
            val futureInstant = Instant.now().plusSeconds(30)

            val result = formatSessionExpiry(futureInstant)

            assertTrue(result.startsWith("in <1m"), "Expected 'in <1m' but got: $result")
        }

        @Test
        fun `should include absolute time in parentheses for future expiry`() {
            val futureInstant = Instant.now().plusSeconds(3600)

            val result = formatSessionExpiry(futureInstant)

            assertTrue(result.matches(Regex("in .+ \\(.+\\)")), "Expected format 'in Xh (absolute)' but got: $result")
        }
    }
}
