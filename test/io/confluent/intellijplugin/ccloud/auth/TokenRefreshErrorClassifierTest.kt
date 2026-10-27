package io.confluent.intellijplugin.ccloud.auth

import com.intellij.util.io.HttpRequests
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.io.IOException
import java.net.SocketTimeoutException

class TokenRefreshErrorClassifierTest {

    private fun endpointError(endpoint: TokenEndpoint, status: Int, retryAfter: String? = null) =
        TokenEndpointException(endpoint, status, retryAfter, "HTTP $status")

    @Nested
    @DisplayName("terminal")
    inner class Terminal {

        @ParameterizedTest(name = "{0} from every token endpoint")
        @ValueSource(ints = [400, 401, 403, 404, 410, 422])
        fun `4xx other than 408 and 429 is terminal`(status: Int) {
            TokenEndpoint.entries.forEach { endpoint ->
                assertTrue(
                    TokenRefreshErrorClassifier.isTerminal(endpointError(endpoint, status)),
                    "$status from $endpoint should be terminal"
                )
            }
        }

        @Test
        fun `invalid_grant is terminal whatever the status`() {
            assertTrue(TokenRefreshErrorClassifier.isTerminal(OAuthErrorException("invalid_grant", null, 403)))
            assertTrue(TokenRefreshErrorClassifier.isTerminal(OAuthErrorException("invalid_grant", null, 200)))
        }

        @Test
        fun `unknown or invalid refresh token is terminal via its status and error code`() {
            val error = OAuthErrorException("invalid_grant", "Unknown or invalid refresh token.", 403)

            assertTrue(TokenRefreshErrorClassifier.isTerminal(error))
        }

        @Test
        fun `missing refresh token is terminal`() {
            assertTrue(TokenRefreshErrorClassifier.isTerminal(MissingRefreshTokenException()))
        }

        @Test
        fun `unrecognised OAuth error with a 4xx status is terminal`() {
            assertTrue(TokenRefreshErrorClassifier.isTerminal(OAuthErrorException("mystery", null, 400)))
        }
    }

    @Nested
    @DisplayName("retryable")
    inner class Retryable {

        @ParameterizedTest(name = "{0}")
        @ValueSource(ints = [408, 429, 500, 502, 503, 504])
        fun `408, 429 and 5xx are retryable`(status: Int) {
            TokenEndpoint.entries.forEach { endpoint ->
                assertFalse(
                    TokenRefreshErrorClassifier.isTerminal(endpointError(endpoint, status)),
                    "$status from $endpoint should be retryable"
                )
            }
        }

        @Test
        fun `OAuth error code on a 429 is still retryable`() {
            assertFalse(TokenRefreshErrorClassifier.isTerminal(OAuthErrorException("invalid_request", null, 429)))
            assertFalse(TokenRefreshErrorClassifier.isTerminal(OAuthErrorException("too_many_requests", null, 429)))
        }

        @Test
        fun `server error status exception is retryable`() {
            val error = HttpRequests.HttpStatusException("Server error", 503, "https://example.invalid")

            assertFalse(TokenRefreshErrorClassifier.isTerminal(error))
        }

        @Test
        fun `IOException is retryable`() {
            assertFalse(TokenRefreshErrorClassifier.isTerminal(IOException("Connection reset")))
            assertFalse(TokenRefreshErrorClassifier.isTerminal(SocketTimeoutException("Read timed out")))
        }

        @Test
        fun `untyped and parse errors are retryable`() {
            assertFalse(TokenRefreshErrorClassifier.isTerminal(IllegalStateException("No auth_token cookie in response")))
            assertFalse(TokenRefreshErrorClassifier.isTerminal(SerializationException("Unexpected JSON token")))
        }

        @Test
        fun `2xx with an error body from a CCloud endpoint is retryable`() {
            assertFalse(TokenRefreshErrorClassifier.isTerminal(endpointError(TokenEndpoint.CONTROL_PLANE, 200)))
        }
    }

    @Nested
    @DisplayName("retryAfterHeader")
    inner class RetryAfterHeader {

        @ParameterizedTest
        @EnumSource(TokenEndpoint::class)
        fun `returns the header for a 429`(endpoint: TokenEndpoint) {
            assertEquals("30", TokenRefreshErrorClassifier.retryAfterHeader(endpointError(endpoint, 429, "30")))
        }

        @Test
        fun `ignores the header on other statuses`() {
            assertNull(TokenRefreshErrorClassifier.retryAfterHeader(endpointError(TokenEndpoint.OAUTH_TOKEN, 503, "30")))
        }

        @Test
        fun `returns null for untyped errors`() {
            assertNull(TokenRefreshErrorClassifier.retryAfterHeader(IOException("boom")))
        }
    }
}
