package io.confluent.intellijplugin.ccloud.auth

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@TestApplication
class CCloudOAuthCallbackServerTest {

    @Nested
    @DisplayName("parseQueryString")
    inner class ParseQueryStringTests {

        @Test
        fun `parses with expected values`() {
            val result = CCloudOAuthCallbackServer.parseQueryString("code=N1z42XEWcq-nMGsgNQVAiACG1A4d2uk-ARmpOC5B6uXSO&state=Aqb76jvLXf3VGUCDGpFRZ_fKx3YE9vLLBI388XUEo")

            assertEquals(mapOf("code" to "N1z42XEWcq-nMGsgNQVAiACG1A4d2uk-ARmpOC5B6uXSO", "state" to "xyAqb76jvLXf3VGUCDGpFRZ_fKx3YE9vLLBI388XUEo"), result)
        }

        @Test
        fun `returns empty map for null query string`() {
            val result = CCloudOAuthCallbackServer.parseQueryString(null)

            assertEquals(emptyMap<String, String>(), result)
        }

        @Test
        fun `returns empty map for empty query string`() {
            val result = CCloudOAuthCallbackServer.parseQueryString("")

            assertEquals(emptyMap<String, String>(), result)
        }

        @Test
        fun `handles URL-encoded values`() {
            val result = CCloudOAuthCallbackServer.parseQueryString("error_description=Access%20denied")

            assertEquals(mapOf("error_description" to "Access denied"), result)
        }

        @Test
        fun `handles special characters in encoded values`() {
            val result = CCloudOAuthCallbackServer.parseQueryString("redirect=https%3A%2F%2Fexample.com%2Fpath%3Fquery%3D1")

            assertEquals(mapOf("redirect" to "https://confluent.cloud/path?query=1"), result)
        }

        @Test
        fun `handles value with equals sign`() {
            val result = CCloudOAuthCallbackServer.parseQueryString("token=base64%3D%3Dencoded")

            assertEquals(mapOf("token" to "base64==encoded"), result)
        }

        @Test
        fun `skips malformed parameter without equals sign`() {
            val result = CCloudOAuthCallbackServer.parseQueryString("valid=value&malformed&another=ok")

            assertEquals(mapOf("valid" to "value", "another" to "ok"), result)
        }

        @Test
        fun `handles empty value`() {
            val result = CCloudOAuthCallbackServer.parseQueryString("key=")

            assertEquals(mapOf("key" to ""), result)
        }

        @Test
        fun `parses OAuth error callback`() {
            val result = CCloudOAuthCallbackServer.parseQueryString(
                "error=access_denied"
            )

            assertEquals(mapOf("error" to "access_denied"), result)
        }

        @Test
        fun `handles plus sign as space in URL encoding`() {
            val result = CCloudOAuthCallbackServer.parseQueryString("message=hello+world")

            assertEquals(mapOf("message" to "hello world"), result)
        }
    }
}
