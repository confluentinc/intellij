package io.confluent.intellijplugin.ccloud.auth

import com.github.tomakehurst.wiremock.client.WireMock
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import com.intellij.util.io.HttpRequests
import io.confluent.intellijplugin.ccloud.util.ResourceLoader
import io.confluent.intellijplugin.testutil.wireMockExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
class CCloudOAuthHttpClientTest {

    companion object {
        @JvmField
        @RegisterExtension
        val wireMock = wireMockExtension()
    }

    private fun baseUrl(): String = wireMock.baseUrl()

    @Nested
    @DisplayName("extractCookie")
    inner class ExtractCookie {

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

    @Nested
    @DisplayName("User-Agent Header")
    inner class UserAgentTests {

        @Test
        fun `includes User-Agent header in post requests`() {
            wireMock.stubFor(
                WireMock.post("/oauth/token")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"access_token": "token"}""")
                    )
            )

            runBlocking {
                CCloudOAuthHttpClient.postFormWithHeaders(
                    url = "${baseUrl()}/oauth/token",
                    formData = mapOf("grant_type" to "authorization_code")
                )
            }

            wireMock.verify(WireMock.postRequestedFor(WireMock.urlEqualTo("/oauth/token"))
                .withHeader("User-Agent", WireMock.matching("confluent-for-intellij/v.+ \\(https://confluent\\.io; support@confluent\\.io\\)")))
        }
    }

    @Nested
    @DisplayName("postFormWithHeaders")
    inner class PostFormWithHeaders {

        @Test
        fun `parses successful token response`() {
            wireMock.stubFor(
                WireMock.post("/oauth/token")
                    .withRequestBody(WireMock.containing("grant_type=authorization_code"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                {
                                    "access_token": "access_123",
                                    "refresh_token": "refresh_456",
                                    "id_token": "id_789",
                                    "expires_in": 101010
                                }
                            """.trimIndent())
                    )
            )

            val result = runBlocking {
                CCloudOAuthHttpClient.postFormWithHeaders(
                    url = "${baseUrl()}/oauth/token",
                    formData = mapOf("grant_type" to "authorization_code", "code" to "test_code")
                )
            }.let { CCloudOAuthHttpClient.json.decodeFromString<IdTokenExchangeResponse>(it.body) }

            assertEquals("access_123", result.accessToken)
            assertEquals("refresh_456", result.refreshToken)
            assertEquals("id_789", result.idToken)
            assertEquals(101010L, result.expiresIn)
            assertNull(result.error)
        }

        @Test
        fun `parses error response`() {
            wireMock.stubFor(
                WireMock.post("/oauth/token")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(400)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"error": "invalid_grant", "error_description": "Bad authorization code"}""")
                    )
            )

            val response = runBlocking {
                CCloudOAuthHttpClient.postFormWithHeaders(
                    url = "${baseUrl()}/oauth/token",
                    formData = mapOf("grant_type" to "authorization_code", "code" to "bad_code")
                )
            }
            val result = CCloudOAuthHttpClient.json.decodeFromString<IdTokenExchangeResponse>(response.body)

            assertEquals(400, response.statusCode)
            assertEquals("invalid_grant", result.error)
            assertTrue(result.errorDescription.toString().contains("Bad authorization code"))
        }

        @Test
        fun `url-encodes special characters`() {
            wireMock.stubFor(
                WireMock.post("/oauth/token")
                    .withRequestBody(WireMock.containing("redirect_uri=http%3A%2F%2Flocalhost"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"access_token": "token"}""")
                    )
            )

            val result = runBlocking {
                CCloudOAuthHttpClient.postFormWithHeaders(
                    url = "${baseUrl()}/oauth/token",
                    formData = mapOf("redirect_uri" to "http://localhost")
                )
            }

            assertEquals(200, result.statusCode)
        }

        @Test
        fun `throws HttpStatusException on 500 server error`() {
            wireMock.stubFor(
                WireMock.post("/oauth/token")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(500)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"error": "server_error", "error_description": "Database unavailable"}""")
                    )
            )

            val exception = assertThrows(HttpRequests.HttpStatusException::class.java) {
                runBlocking {
                    CCloudOAuthHttpClient.postFormWithHeaders(
                        url = "${baseUrl()}/oauth/token",
                        formData = mapOf("grant_type" to "authorization_code")
                    )
                }
            }

            assertEquals(500, exception.statusCode)
            assertTrue(exception.message!!.contains("Database unavailable"))
        }
    }

    @Nested
    @DisplayName("postJsonWithHeaders")
    inner class PostJsonWithHeaders {

        @Test
        fun `returns status code and extracts auth token from cookie`() {
            wireMock.stubFor(
                WireMock.post("/api/sessions")
                    .withRequestBody(WireMock.matchingJsonPath("$.id_token"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withHeader("Set-Cookie", "auth_token=cp_token_xyz; Path=/")
                            .withBody("""{"user": {"id": "u-123", "email": "test@example.com"}}""")
                    )
            )

            val result = runBlocking {
                CCloudOAuthHttpClient.postJsonWithHeaders(
                    url = "${baseUrl()}/api/sessions",
                    jsonBody = """{"id_token":"test_token"}"""
                )
            }

            assertEquals(200, result.statusCode)
            assertEquals("cp_token_xyz", CCloudOAuthHttpClient.extractCookie(result.headers, "auth_token"))

            val parsed = CCloudOAuthHttpClient.json.decodeFromString<ControlPlaneTokenExchangeResponse>(result.body)
            assertEquals("u-123", parsed.user?.id)
            assertEquals("test@example.com", parsed.user?.email)
        }

        @Test
        fun `handles error response with status code`() {
            // Use 400 instead of 401 - Java's HttpURLConnection has special auth handling for 401
            // that can interfere with reading the error body
            wireMock.stubFor(
                WireMock.post("/api/sessions")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(400)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"error": {"code": 400, "message": "Bad Request"}}""")
                    )
            )

            val request = ControlPlaneTokenExchangeRequest(idToken = "bad_token")

            val result = runBlocking {
                CCloudOAuthHttpClient.postJsonWithHeaders(
                    url = "${baseUrl()}/api/sessions",
                    jsonBody = CCloudOAuthHttpClient.json.encodeToString(request)
                )
            }

            assertEquals(400, result.statusCode)
            val parsed = CCloudOAuthHttpClient.json.decodeFromString<ControlPlaneTokenExchangeResponse>(result.body)
            assertNotNull(parsed.error)
            assertTrue(parsed.error.toString().contains("400"))
            assertTrue(parsed.error.toString().contains("Bad Request"))
        }

        @Test
        fun `throws HttpStatusException on 500 server error`() {
            wireMock.stubFor(
                WireMock.post("/api/sessions")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(500)
                            .withBody("Internal Server Error")
                    )
            )

            val exception = assertThrows(HttpRequests.HttpStatusException::class.java) {
                runBlocking {
                    CCloudOAuthHttpClient.postJsonWithHeaders(
                        url = "${baseUrl()}/api/sessions",
                        jsonBody = """{"id_token":"test"}"""
                    )
                }
            }

            assertEquals(500, exception.statusCode)
            assertTrue(exception.message!!.contains("Server error"))
        }

        @Test
        fun `sends bearer token when given`() {
            wireMock.stubFor(
                WireMock.post("/api/access_tokens")
                    .withHeader("Authorization", WireMock.equalTo("Bearer cp_token"))
                    .withRequestBody(WireMock.equalTo("{}"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(ResourceLoader.loadResource("fixtures/ccloud-auth/data-plane-token-success.json"))
                    )
            )

            val result = runBlocking {
                CCloudOAuthHttpClient.postJsonWithHeaders(
                    url = "${baseUrl()}/api/access_tokens",
                    jsonBody = "{}",
                    bearerToken = "cp_token"
                )
            }

            assertEquals(200, result.statusCode)
        }
    }

    @Nested
    @DisplayName("HttpResponseWithHeaders")
    inner class ResponseWithHeaders {

        @Test
        fun `reads Retry-After case-insensitively`() {
            val response = CCloudOAuthHttpClient.HttpResponseWithHeaders(
                statusCode = 429,
                body = "",
                headers = mapOf("retry-after" to listOf("30"))
            )

            assertEquals("30", response.retryAfterHeader)
            assertFalse(response.isSuccessful)
        }
    }
}
