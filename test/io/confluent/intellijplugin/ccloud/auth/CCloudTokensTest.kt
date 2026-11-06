package io.confluent.intellijplugin.ccloud.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.Instant
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

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

    @Nested
    @DisplayName("Token.issued")
    inner class Issued {

        private val issuedAt = Instant.parse("2026-11-06T19:00:00Z")

        /** A [Random] whose `nextDouble()` is always [value], so the jitter draw is known. */
        private fun fixedRandom(value: Double) = object : Random() {
            override fun nextBits(bitCount: Int): Int = error("not used by nextDouble")
            override fun nextDouble(): Double = value
        }

        @Test
        fun `expires one lifetime after it was issued`() {
            val token = Token.issued("cp", issuedAt, 300.seconds)

            assertEquals(Instant.parse("2026-11-06T19:05:00Z"), token.expiresAt)
        }

        @Test
        fun `earliest refresh point is 70 percent of the lifetime`() {
            val token = Token.issued("cp", issuedAt, 300.seconds, fixedRandom(0.0))

            assertEquals(Instant.parse("2026-11-06T19:03:30Z"), token.refreshAt)
        }

        @Test
        fun `latest refresh point stays just under 90 percent of the lifetime`() {
            val token = Token.issued("cp", issuedAt, 300.seconds, fixedRandom(0.999_999))

            assertTrue(token.refreshAt < Instant.parse("2026-11-06T19:04:30Z"), "refreshAt=${token.refreshAt}")
            assertTrue(token.refreshAt > Instant.parse("2026-11-06T19:04:29Z"), "refreshAt=${token.refreshAt}")
        }

        @Test
        fun `refresh points are jittered across the 70 to 90 percent window`() {
            val random = Random(42)
            val refreshPoints = (1..1_000).map { Token.issued("cp", issuedAt, 300.seconds, random).refreshAt }

            val earliest = Instant.parse("2026-11-06T19:03:30Z")
            val latest = Instant.parse("2026-11-06T19:04:30Z")
            assertTrue(refreshPoints.all { it >= earliest && it < latest }, "out of window: ${refreshPoints.filterNot { it >= earliest && it < latest }}")
            // Spread across the window, not clustered at one point
            assertTrue(refreshPoints.min() < Instant.parse("2026-11-06T19:03:40Z"), "min=${refreshPoints.min()}")
            assertTrue(refreshPoints.max() > Instant.parse("2026-11-06T19:04:20Z"), "max=${refreshPoints.max()}")
        }
    }

    @Nested
    @DisplayName("isRefreshDueBefore")
    inner class IsRefreshDueBefore {

        private val t0 = Instant.parse("2026-11-06T19:00:00Z")
        private val controlPlane = Token("cp", expiresAt = t0.plusSeconds(300), refreshAt = t0.plusSeconds(210))
        private val dataPlane = Token("dp", expiresAt = t0.plusSeconds(900), refreshAt = t0.plusSeconds(630))
        private val refreshToken = Token("rt", expiresAt = t0.plusSeconds(10_800))
        private val tokens = Tokens(refreshToken = refreshToken, controlPlaneToken = controlPlane, dataPlaneToken = dataPlane)

        @Test
        fun `is not due when the deadline is at or before the earliest refresh point`() {
            assertFalse(tokens.isRefreshDueBefore(t0.plusSeconds(210)))
        }

        @Test
        fun `is due once the deadline passes the earliest refresh point, long before any token expires`() {
            assertTrue(tokens.isRefreshDueBefore(t0.plusSeconds(211)))
        }

        @Test
        fun `a token built without a refresh point is due at its expiry`() {
            val onlyRefreshToken = Tokens(refreshToken = refreshToken)

            assertFalse(onlyRefreshToken.isRefreshDueBefore(t0.plusSeconds(10_800)))
            assertTrue(onlyRefreshToken.isRefreshDueBefore(t0.plusSeconds(10_801)))
        }

        @Test
        fun `is never due without tokens`() {
            assertFalse(Tokens().isRefreshDueBefore(Instant.MAX))
        }
    }
}
