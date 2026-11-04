package io.confluent.intellijplugin.ccloud.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.IOException

class CCloudTokensTest {

    private val max = CCloudOAuthConfig.MAX_TOKEN_REFRESH_ATTEMPTS

    private fun failTimes(n: Int, error: Throwable = IOException("Connection reset")): Tokens =
        (1..n).fold(Tokens()) { tokens, _ -> tokens.withFailedTokenRefreshAttempt(error, max) }

    @Nested
    @DisplayName("withFailedTokenRefreshAttempt")
    inner class WithFailedTokenRefreshAttempt {

        @Test
        fun `cap is 10 attempts`() {
            assertEquals(10, max)
        }

        @Test
        fun `first 9 transient failures stay transient`() {
            val tokens = failTimes(max - 1)

            assertEquals(max - 1, tokens.failedTokenRefreshAttempts)
            assertFalse(tokens.errors.hasNonTransientErrors())
        }

        @Test
        fun `10th failure is non-transient`() {
            val tokens = failTimes(max)

            assertEquals(max, tokens.failedTokenRefreshAttempts)
            assertTrue(tokens.errors.hasNonTransientErrors())
        }

        @Test
        fun `terminal error is non-transient on the first attempt`() {
            val tokens = failTimes(1, TokenEndpointException(TokenEndpoint.CONTROL_PLANE, 401, message = "HTTP 401"))

            assertEquals(1, tokens.failedTokenRefreshAttempts)
            assertTrue(tokens.errors.hasNonTransientErrors())
        }

        @Test
        fun `retryable status is transient on the first attempt`() {
            val tokens = failTimes(1, TokenEndpointException(TokenEndpoint.OAUTH_TOKEN, 429, "5", "HTTP 429"))

            assertFalse(tokens.errors.hasNonTransientErrors())
        }
    }

    @Nested
    @DisplayName("withSuccessfulTokenRefreshAttempt")
    inner class WithSuccessfulTokenRefreshAttempt {

        @Test
        fun `resets the counter and the refresh error`() {
            val tokens = failTimes(4).withSuccessfulTokenRefreshAttempt()

            assertEquals(0, tokens.failedTokenRefreshAttempts)
            assertNull(tokens.errors.tokenRefresh)
        }
    }
}
