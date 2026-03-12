package io.confluent.intellijplugin.telemetry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CCloudAuthenticationEventTest {

    @Nested
    @DisplayName("SignedIn")
    inner class SignedIn {

        @Test
        fun `should have correct event name and status`() {
            val event = CCloudAuthenticationEvent.SignedIn()

            assertEquals("CCloud Authentication", event.eventName)
            assertEquals("signed in", event.status)
        }

        @Test
        fun `should include all properties when provided`() {
            val event = CCloudAuthenticationEvent.SignedIn(
                ccloudId = "u-123",
                invokedPlace = "welcome_panel",
            )

            assertEquals(mapOf(
                "status" to "signed in",
                "ccloudId" to "u-123",
                "invokedPlace" to "welcome_panel",
            ), event.properties())
        }

        @Test
        fun `should omit null properties`() {
            val event = CCloudAuthenticationEvent.SignedIn()

            assertEquals(mapOf("status" to "signed in"), event.properties())
        }
    }

    @Nested
    @DisplayName("SignedOut")
    inner class SignedOut {

        @Test
        fun `should have correct event name and status`() {
            val event = CCloudAuthenticationEvent.SignedOut(reason = "user_initiated")

            assertEquals("CCloud Authentication", event.eventName)
            assertEquals("signed out", event.status)
        }

        @Test
        fun `should include reason and invokedPlace`() {
            val event = CCloudAuthenticationEvent.SignedOut(
                reason = "user_initiated",
                invokedPlace = "settings_panel",
            )

            assertEquals(mapOf(
                "status" to "signed out",
                "reason" to "user_initiated",
                "invokedPlace" to "settings_panel",
            ), event.properties())
        }

        @Test
        fun `should omit invokedPlace when null`() {
            val event = CCloudAuthenticationEvent.SignedOut(reason = "session_expired")

            assertEquals(mapOf(
                "status" to "signed out",
                "reason" to "session_expired",
            ), event.properties())
        }
    }

    @Nested
    @DisplayName("AuthenticationFailed")
    inner class AuthenticationFailed {

        @Test
        fun `should have correct event name and status`() {
            val event = CCloudAuthenticationEvent.AuthenticationFailed(errorType = "timeout")

            assertEquals("CCloud Authentication", event.eventName)
            assertEquals("authentication failed", event.status)
        }

        @Test
        fun `should include all properties when provided`() {
            val event = CCloudAuthenticationEvent.AuthenticationFailed(
                errorType = "invalid_grant",
                invokedPlace = "tool_window_action",
            )

            assertEquals(mapOf(
                "status" to "authentication failed",
                "errorType" to "invalid_grant",
                "invokedPlace" to "tool_window_action",
            ), event.properties())
        }

        @Test
        fun `should omit invokedPlace when null`() {
            val event = CCloudAuthenticationEvent.AuthenticationFailed(errorType = "timeout")

            assertEquals(mapOf(
                "status" to "authentication failed",
                "errorType" to "timeout",
            ), event.properties())
        }
    }

    @Nested
    @DisplayName("TokenRefreshFailed")
    inner class TokenRefreshFailed {

        @Test
        fun `should have correct event name and status`() {
            val event = CCloudAuthenticationEvent.TokenRefreshFailed()

            assertEquals("CCloud Authentication", event.eventName)
            assertEquals("token refresh failed", event.status)
        }

        @Test
        fun `should include errorType when provided`() {
            val event = CCloudAuthenticationEvent.TokenRefreshFailed(
                errorType = "401 Unauthorized",
            )

            assertEquals(mapOf(
                "status" to "token refresh failed",
                "errorType" to "401 Unauthorized",
            ), event.properties())
        }

        @Test
        fun `should omit errorType when null`() {
            val event = CCloudAuthenticationEvent.TokenRefreshFailed()

            assertEquals(mapOf(
                "status" to "token refresh failed",
            ), event.properties())
        }
    }
}
