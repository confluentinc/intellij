package io.confluent.intellijplugin.ccloud.auth

import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException

@TestApplication
class CCloudOAuthCallbackServerTest {

    @Nested
    @DisplayName("parseQueryString")
    inner class ParseQueryStringTests {

        @Test
        fun `parses with expected values`() {
            val result = CCloudOAuthCallbackServer.parseQueryString("code=N1z42XEWcq-nMGsgNQVAiACG1A4d2uk-ARmpOC5B6uXSO&state=Aqb76jvLXf3VGUCDGpFRZ_fKx3YE9vLLBI388XUEo")

            assertEquals(mapOf("code" to "N1z42XEWcq-nMGsgNQVAiACG1A4d2uk-ARmpOC5B6uXSO", "state" to "Aqb76jvLXf3VGUCDGpFRZ_fKx3YE9vLLBI388XUEo"), result)
        }

        @Test
        fun `returns empty map for null or empty query string`() {
            assertEquals(emptyMap<String, String>(), CCloudOAuthCallbackServer.parseQueryString(null))
            assertEquals(emptyMap<String, String>(), CCloudOAuthCallbackServer.parseQueryString(""))
        }

        @Test
        fun `handles URL-encoded values`() {
            val result = CCloudOAuthCallbackServer.parseQueryString("error_description=Access%20denied")

            assertEquals(mapOf("error_description" to "Access denied"), result)
        }

        @Test
        fun `handles special characters in encoded values`() {
            val result = CCloudOAuthCallbackServer.parseQueryString("redirect=https%3A%2F%2Fconfluent.cloud%2Fpath%3Fquery%3D1")

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
    }

    @Nested
    @DisplayName("processCallback")
    inner class ProcessCallbackTests {

        private lateinit var server: CCloudOAuthCallbackServer
        private lateinit var context: CCloudOAuthContext

        @BeforeEach
        fun setUp() {
            context = CCloudOAuthContext()
            server = CCloudOAuthCallbackServer(
                oauthContext = context,
                onSuccess = {},
                onError = {}
            )
        }

        @Test
        fun `error parameter returns 400 with error message`() = runBlocking {
            val result = server.processCallback(code = null, state = null, error = "access_denied")

            assertEquals(400, result.statusCode)
            assertEquals("access_denied", result.errorMessage)
            assertNull(result.successContext)
            assertTrue(result.html.contains("access_denied"))
        }

        @Test
        fun `invalid state returns 400`() = runBlocking {
            val result = server.processCallback(code = "some-code", state = "wrong-state", error = null)

            assertEquals(400, result.statusCode)
            assertEquals("Invalid state parameter", result.errorMessage)
            assertNull(result.successContext)
        }

        @Test
        fun `missing code returns 400`() = runBlocking {
            val result = server.processCallback(code = null, state = context.oauthState, error = null)

            assertEquals(400, result.statusCode)
            assertEquals("Missing authorization code", result.errorMessage)
            assertNull(result.successContext)
        }

        @Test
        fun `valid code and state returns 200 with success context`() = runBlocking {
            val mockContext = mock<CCloudOAuthContext> {
                on { oauthState } doReturn "test-state"
                on { getUserEmail() } doReturn "user@example.com"
                onBlocking { createTokensFromAuthorizationCode("valid-code") } doReturn Result.success(mock)
            }

            val testServer = CCloudOAuthCallbackServer(
                oauthContext = mockContext,
                onSuccess = {},
                onError = {}
            )

            val result = testServer.processCallback(code = "valid-code", state = "test-state", error = null)

            assertEquals(200, result.statusCode)
            assertNotNull(result.successContext)
            assertNull(result.errorMessage)
            assertTrue(result.html.contains("user@example.com"))
        }

        private fun serverWithFailingTokenExchange(failure: Throwable): CCloudOAuthCallbackServer {
            val mockContext = mock<CCloudOAuthContext> {
                on { oauthState } doReturn "test-state"
                onBlocking { createTokensFromAuthorizationCode("code") } doReturn Result.failure(failure)
            }
            return CCloudOAuthCallbackServer(
                oauthContext = mockContext,
                onSuccess = {},
                onError = {}
            )
        }

        @Test
        fun `token exchange failure returns 500`() = runBlocking {
            val testServer = serverWithFailingTokenExchange(RuntimeException("Network error"))

            val result = testServer.processCallback(code = "code", state = "test-state", error = null)

            assertEquals(500, result.statusCode)
            assertEquals("Network error", result.errorMessage)
            assertNull(result.successContext)
        }

        @Test
        fun `SSLHandshakeException returns TLS-specific error message`() = runBlocking {
            val testServer = serverWithFailingTokenExchange(SSLHandshakeException("cert error"))

            val result = testServer.processCallback(code = "code", state = "test-state", error = null)

            assertEquals(500, result.statusCode)
            assertTrue(result.errorMessage!!.contains("SSL/TLS handshake"))
            assertNull(result.successContext)
        }

        @Test
        fun `invalid_grant error returns 400 with user-friendly message`() = runBlocking {
            val testServer = serverWithFailingTokenExchange(
                OAuthErrorException("invalid_grant", "Authorization code has expired", 400)
            )

            val result = testServer.processCallback(code = "code", state = "test-state", error = null)

            assertEquals(400, result.statusCode)
            assertTrue(result.errorMessage!!.contains("invalid_grant"))
            assertNull(result.successContext)
        }

        @Test
        fun `access_denied error returns 400`() = runBlocking {
            val testServer = serverWithFailingTokenExchange(
                OAuthErrorException("access_denied", "User denied access", 400)
            )

            val result = testServer.processCallback(code = "code", state = "test-state", error = null)

            assertEquals(400, result.statusCode)
            assertTrue(result.errorMessage!!.contains("access_denied"))
            assertNull(result.successContext)
        }

        @Test
        fun `invalid_request error returns 400`() = runBlocking {
            val testServer = serverWithFailingTokenExchange(
                OAuthErrorException("invalid_request", "Missing required parameter", 400)
            )

            val result = testServer.processCallback(code = "code", state = "test-state", error = null)

            assertEquals(400, result.statusCode)
            assertTrue(result.errorMessage!!.contains("invalid_request"))
            assertNull(result.successContext)
        }

        @Test
        fun `unknown error returns 500`() = runBlocking {
            val testServer = serverWithFailingTokenExchange(RuntimeException("Connection refused"))

            val result = testServer.processCallback(code = "code", state = "test-state", error = null)

            assertEquals(500, result.statusCode)
            assertEquals("Connection refused", result.errorMessage)
            assertNull(result.successContext)
        }
    }

    @Nested
    @DisplayName("error handling")
    inner class ErrorHandlingTests {

        private val server = CCloudOAuthCallbackServer(
            oauthContext = CCloudOAuthContext(),
            onSuccess = {},
            onError = {}
        )

        @Test
        fun `getErrorStatusAndMessage returns OAuthErrorException httpStatusCode`() {
            val (status, _) = server.getErrorStatusAndMessage(
                OAuthErrorException("invalid_grant", "Authorization code has expired", 400)
            )
            assertEquals(400, status)
        }

        @Test
        fun `getErrorStatusAndMessage returns 500 for SSLHandshakeException`() {
            val (status, message) = server.getErrorStatusAndMessage(SSLHandshakeException("cert error"))
            assertEquals(500, status)
            assertTrue(message.contains("SSL/TLS handshake"))
        }

        @Test
        fun `getErrorStatusAndMessage returns 500 for generic exceptions`() {
            val (status, message) = server.getErrorStatusAndMessage(RuntimeException("Connection refused"))
            assertEquals(500, status)
            assertEquals("Connection refused", message)
        }

        @Test
        fun `getErrorStatusAndMessage uses fallback for exception with null message`() {
            val (status, message) = server.getErrorStatusAndMessage(RuntimeException())
            assertEquals(500, status)
            assertEquals("Token exchange failed", message)
        }
    }

    @Nested
    @DisplayName("HTML rendering")
    inner class HtmlRenderingTests {

        private val server = CCloudOAuthCallbackServer(
            oauthContext = CCloudOAuthContext(),
            onSuccess = {},
            onError = {}
        )

        @Test
        fun `success page contains email and Confluent Cloud link`() {
            val html = server.successHtml("user@example.com")

            assertTrue(html.contains("user@example.com"))
            assertTrue(html.contains("https://confluent.cloud"))
        }

        @Test
        fun `error page contains error message`() {
            val html = server.errorHtml("Something went wrong")

            assertTrue(html.contains("Something went wrong"))
        }

        @Test
        fun `escapeHtml prevents XSS`() {
            val html = server.errorHtml("<script>alert('xss')</script>")

            assertFalse(html.contains("<script>"))
            assertTrue(html.contains("&lt;script&gt;"))
        }
    }

    @Nested
    @DisplayName("server lifecycle")
    inner class ServerLifecycleTests {

        private lateinit var server: CCloudOAuthCallbackServer
        private val testPort = 26639

        @BeforeEach
        fun setUp() {
            System.setProperty("ccloud.callback-port", testPort.toString())
            server = CCloudOAuthCallbackServer(
                oauthContext = CCloudOAuthContext(),
                onSuccess = {},
                onError = {}
            )
        }

        @AfterEach
        fun tearDown() {
            server.stop()
            System.clearProperty("ccloud.callback-port")
        }

        @Test
        fun `start accepts connections`() {
            server.start()

            val response = httpGet("error=test")
            assertEquals(400, response.first)
        }

        @Test
        fun `stop rejects connections`() {
            server.start()
            server.stop()

            assertThrows<Exception> { httpGet("error=test") }
        }

        @Test
        fun `double start is idempotent`() {
            server.start()
            server.start()

            val response = httpGet("error=test")
            assertEquals(400, response.first)
        }

        @Test
        fun `double stop is idempotent`() {
            server.start()
            server.stop()
            server.stop() // Should not throw
        }

        @Test
        fun `onError callback is invoked when error parameter is present`() {
            var capturedError: String? = null
            val latch = CountDownLatch(1)
            val errorServer = CCloudOAuthCallbackServer(
                oauthContext = CCloudOAuthContext(),
                onSuccess = {},
                onError = { msg -> capturedError = msg; latch.countDown() }
            )
            errorServer.start()

            httpGet("error=access_denied")
            assertTrue(latch.await(5, TimeUnit.SECONDS), "onError callback should be invoked")
            assertEquals("access_denied", capturedError)
        }

        @Test
        fun `onSuccess callback is invoked with valid auth flow`() {
            var successInvoked = false
            val latch = CountDownLatch(1)
            val mockContext = mock<CCloudOAuthContext> {
                on { oauthState } doReturn "test-state"
                on { getUserEmail() } doReturn "user@test.com"
                onBlocking { createTokensFromAuthorizationCode("valid-code") } doReturn Result.success(mock)
            }
            val successServer = CCloudOAuthCallbackServer(
                oauthContext = mockContext,
                onSuccess = { successInvoked = true; latch.countDown() },
                onError = {}
            )
            successServer.start()

            httpGet("code=valid-code&state=test-state")
            assertTrue(latch.await(5, TimeUnit.SECONDS), "onSuccess callback should be invoked")
            assertTrue(successInvoked)
        }

        @Test
        fun `server stops after handling a callback`() {
            val latch = CountDownLatch(1)
            val callbackServer = CCloudOAuthCallbackServer(
                oauthContext = CCloudOAuthContext(),
                onSuccess = {},
                onError = { latch.countDown() }
            )
            callbackServer.start()

            httpGet("error=test")
            assertTrue(latch.await(5, TimeUnit.SECONDS), "callback should complete")

            // Give the async stop() a moment to execute
            Thread.sleep(500)
            assertThrows<Exception> { httpGet("error=test2") }
        }

        private fun httpGet(query: String): Pair<Int, String> {
            val url = URI("http://127.0.0.1:$testPort${CCloudOAuthConfig.CALLBACK_PATH}?$query").toURL()
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 2000
                readTimeout = 2000
            }
            return try {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.responseCode to body
            } catch (e: Exception) {
                val body = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                conn.responseCode to body
            } finally {
                conn.disconnect()
            }
        }
    }
}
