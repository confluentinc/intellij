package io.confluent.intellijplugin.ccloud.auth

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull

@TestApplication
class CCloudOAuthHttpClientTest {

    @Nested
    @DisplayName("extractCookie")
    inner class ExtractCookieTests {

        @Test
        fun `returns cookie value when present`() {
            val headers = mapOf("Set-Cookie" to listOf("auth_token=tOkEN0Auth; Path=/"))

            val result = CCloudOAuthHttpClient.extractCookie(headers, "auth_token")

            assertEquals("tOkEN0Auth", result)
        }

        @Test
        fun `returns null when cookie not found`() {
            val headers = mapOf("Set-Cookie" to listOf("other_cookie=value"))

            val result = CCloudOAuthHttpClient.extractCookie(headers, "auth_token")

            assertNull(result)
        }

        @Test
        fun `handles lowercase set-cookie header`() {
            val headers = mapOf("set-cookie" to listOf("auth_token=lowercase_token"))

            val result = CCloudOAuthHttpClient.extractCookie(headers, "auth_token")

            assertEquals("lowercase_token", result)
        }

        @Test
        fun `extracts correct cookie from multiple cookies`() {
            val headers = mapOf(
                "Set-Cookie" to listOf(
                    "session_id=not_wanted; Path=/",
                    "auth_token=target_value;",
                    "tracking=not_wanted;"
                )
            )

            val result = CCloudOAuthHttpClient.extractCookie(headers, "auth_token")

            assertEquals("target_value", result)
        }

        @Test
        fun `handles cookie value with equals sign`() {
            val headers = mapOf("Set-Cookie" to listOf("auth_token=base64==encoded; Path=/"))

            val result = CCloudOAuthHttpClient.extractCookie(headers, "auth_token")

            assertEquals("base64==encoded", result)
        }
    }
}
