package io.confluent.intellijplugin.telemetry

import io.confluent.intellijplugin.ccloud.auth.InvokedPlace
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CCloudAuthenticationEventTest {

    @Nested
    @DisplayName("All events share the same event name")
    inner class SharedEventName {

        @Test
        fun `all event types should use the same event name for consistent telemetry tracking`() {
            val events = listOf(
                CCloudAuthenticationEvent.SignedIn(),
                CCloudAuthenticationEvent.SignedOut(reason = "user_initiated"),
                CCloudAuthenticationEvent.AuthenticationFailed(errorType = "timeout"),
                CCloudAuthenticationEvent.TokenRefreshFailed(),
            )

            events.forEach {
                assertEquals("CCloud Authentication", it.eventName)
            }
        }

        @Test
        fun `all event types should include status in properties`() {
            val events = listOf(
                CCloudAuthenticationEvent.SignedIn(),
                CCloudAuthenticationEvent.SignedOut(reason = "user_initiated"),
                CCloudAuthenticationEvent.AuthenticationFailed(errorType = "timeout"),
                CCloudAuthenticationEvent.TokenRefreshFailed(),
            )

            events.forEach {
                assertTrue(it.properties().containsKey("status"), "${it::class.simpleName} missing 'status' in properties")
            }
        }

        @Test
        fun `each event type should have a distinct status value`() {
            val statuses = listOf(
                CCloudAuthenticationEvent.SignedIn().status,
                CCloudAuthenticationEvent.SignedOut(reason = "test").status,
                CCloudAuthenticationEvent.AuthenticationFailed(errorType = "test").status,
                CCloudAuthenticationEvent.TokenRefreshFailed().status,
            )

            assertEquals(statuses.size, statuses.toSet().size, "Status values must be unique across event types")
        }
    }

    @Nested
    @DisplayName("SignedIn")
    inner class SignedIn {

        @Test
        fun `should have correct status`() {
            assertEquals("signed in", CCloudAuthenticationEvent.SignedIn().status)
        }

        @Test
        fun `should include ccloudUserId and ccloudDomain from sign-in flow`() {
            val event = CCloudAuthenticationEvent.SignedIn(
                ccloudUserId = "u-abc123",
                ccloudDomain = "confluent.io",
                invokedPlace = InvokedPlace.WELCOME_PANEL.value,
            )

            val props = event.properties()
            assertEquals("u-abc123", props["ccloudUserId"])
            assertEquals("confluent.io", props["ccloudDomain"])
            assertEquals("welcome_panel", props["invokedPlace"])
        }

        @Test
        fun `should exclude all optional properties when user info is unavailable`() {
            val event = CCloudAuthenticationEvent.SignedIn()
            val props = event.properties()

            assertEquals(mapOf("status" to "signed in"), props)
            assertFalse(props.containsKey("ccloudUserId"))
            assertFalse(props.containsKey("ccloudDomain"))
            assertFalse(props.containsKey("invokedPlace"))
        }

        @Test
        fun `should support all InvokedPlace values`() {
            InvokedPlace.entries.forEach { place ->
                val event = CCloudAuthenticationEvent.SignedIn(invokedPlace = place.value)
                assertEquals(place.value, event.properties()["invokedPlace"])
            }
        }
    }

    @Nested
    @DisplayName("SignedOut")
    inner class SignedOut {

        @Test
        fun `should have correct status`() {
            assertEquals("signed out", CCloudAuthenticationEvent.SignedOut(reason = "user_initiated").status)
        }

        @Test
        fun `should track user-initiated sign-out with invokedPlace`() {
            val event = CCloudAuthenticationEvent.SignedOut(
                reason = "user_initiated",
                invokedPlace = InvokedPlace.SETTINGS_PANEL.value,
            )

            val props = event.properties()
            assertEquals("user_initiated", props["reason"])
            assertEquals("settings_panel", props["invokedPlace"])
        }

        @Test
        fun `should track session-expired sign-out without invokedPlace`() {
            val event = CCloudAuthenticationEvent.SignedOut(reason = "session_expired")

            val props = event.properties()
            assertEquals("session_expired", props["reason"])
            assertFalse(props.containsKey("invokedPlace"))
        }
    }

    @Nested
    @DisplayName("AuthenticationFailed")
    inner class AuthenticationFailed {

        @Test
        fun `should have correct status`() {
            assertEquals("authentication failed", CCloudAuthenticationEvent.AuthenticationFailed(errorType = "timeout").status)
        }

        @Test
        fun `should track timeout error without invokedPlace`() {
            val event = CCloudAuthenticationEvent.AuthenticationFailed(errorType = "timeout")

            val props = event.properties()
            assertEquals("timeout", props["errorType"])
            assertFalse(props.containsKey("invokedPlace"))
        }

        @Test
        fun `errorType should always be present in properties`() {
            val event = CCloudAuthenticationEvent.AuthenticationFailed(errorType = "unknown")
            assertTrue(event.properties().containsKey("errorType"))
        }
    }

    @Nested
    @DisplayName("TokenRefreshFailed")
    inner class TokenRefreshFailed {

        @Test
        fun `should have correct status`() {
            assertEquals("token refresh failed", CCloudAuthenticationEvent.TokenRefreshFailed().status)
        }

        @Test
        fun `should omit errorType when not provided`() {
            val event = CCloudAuthenticationEvent.TokenRefreshFailed()

            val props = event.properties()
            assertEquals(mapOf("status" to "token refresh failed"), props)
            assertFalse(props.containsKey("errorType"))
        }

        @Test
        fun `should only contain status when errorType is null`() {
            val event = CCloudAuthenticationEvent.TokenRefreshFailed(errorType = null)
            assertEquals(setOf("status"), event.properties().keys)
        }
    }
}