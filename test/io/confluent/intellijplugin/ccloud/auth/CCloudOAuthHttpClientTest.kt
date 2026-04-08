package io.confluent.intellijplugin.ccloud.auth

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import com.intellij.util.io.HttpRequests
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows

@TestApplication
class CCloudOAuthHttpClientTest {

    companion object {
        private lateinit var wireMockServer: WireMockServer

        @JvmStatic
        @BeforeAll
        fun setUpAll() {
            wireMockServer = WireMockServer(wireMockConfig().dynamicPort())
            wireMockServer.start()
        }

        @JvmStatic
        @AfterAll
        fun tearDownAll() {
            wireMockServer.stop()
        }
    }

    @AfterEach
    fun cleanUp() {
        wireMockServer.resetAll()
    }

    private fun baseUrl(): String = "http://localhost:${wireMockServer.port()}"

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
        fun `includes User-Agent header in postForm requests`() {
            wireMockServer.stubFor(
                WireMock.post("/oauth/token")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"access_token": "token"}""")
                    )
            )

            runBlocking {
                CCloudOAuthHttpClient.postForm<IdTokenExchangeResponse>(
                    url = "${baseUrl()}/oauth/token",
                    formData = mapOf("grant_type" to "authorization_code")
                )
            }

            wireMockServer.verify(WireMock.postRequestedFor(WireMock.urlEqualTo("/oauth/token"))
                .withHeader("User-Agent", WireMock.matching("confluent-for-intellij/v.+ \\(https://confluent\\.io; support@confluent\\.io\\)")))
        }

        @Test
        fun `includes User-Agent header in get requests`() {
            wireMockServer.stubFor(
                WireMock.get("/api/check_jwt")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"claims": {"prop": "user-123"}}""")
                    )
            )

            runBlocking {
                CCloudOAuthHttpClient.get<CheckJwtResponse>(
                    url = "${baseUrl()}/api/check_jwt",
                    bearerToken = "token"
                )
            }

            wireMockServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/api/check_jwt"))
                .withHeader("User-Agent", WireMock.matching("confluent-for-intellij/v.+ \\(https://confluent\\.io; support@confluent\\.io\\)")))
        }
    }

    @Nested
    @DisplayName("postForm")
    inner class PostForm {

        @Test
        fun `parses successful token response`() {
            wireMockServer.stubFor(
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
                CCloudOAuthHttpClient.postForm<IdTokenExchangeResponse>(
                    url = "${baseUrl()}/oauth/token",
                    formData = mapOf("grant_type" to "authorization_code", "code" to "test_code")
                )
            }

            assertEquals("access_123", result.accessToken)
            assertEquals("refresh_456", result.refreshToken)
            assertEquals("id_789", result.idToken)
            assertEquals(101010L, result.expiresIn)
            assertNull(result.error)
        }

        @Test
        fun `parses error response`() {
            wireMockServer.stubFor(
                WireMock.post("/oauth/token")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(400)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"error": "invalid_grant", "error_description": "Bad authorization code"}""")
                    )
            )

            val result = runBlocking {
                CCloudOAuthHttpClient.postForm<IdTokenExchangeResponse>(
                    url = "${baseUrl()}/oauth/token",
                    formData = mapOf("grant_type" to "authorization_code", "code" to "bad_code")
                )
            }

            assertEquals("invalid_grant", result.error)
            assertTrue(result.errorDescription.toString().contains("Bad authorization code"))
        }

        @Test
        fun `url-encodes special characters`() {
            wireMockServer.stubFor(
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
                CCloudOAuthHttpClient.postForm<IdTokenExchangeResponse>(
                    url = "${baseUrl()}/oauth/token",
                    formData = mapOf("redirect_uri" to "http://localhost")
                )
            }

            assertEquals("token", result.accessToken)
        }

        @Test
        fun `throws HttpStatusException on 500 server error`() {
            wireMockServer.stubFor(
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
                    CCloudOAuthHttpClient.postForm<IdTokenExchangeResponse>(
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
            wireMockServer.stubFor(
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
            wireMockServer.stubFor(
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
            wireMockServer.stubFor(
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
    }

    @Nested
    @DisplayName("postJson")
    inner class PostJson {

        @Test
        fun `parses data plane token response`() {
            wireMockServer.stubFor(
                WireMock.post("/api/access_tokens")
                    .withHeader("Authorization", WireMock.equalTo("Bearer cp_token"))
                    .withRequestBody(WireMock.equalTo("{}"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"token": "dp_token_xyz"}""")
                    )
            )

            val result = runBlocking {
                CCloudOAuthHttpClient.postJson<DataPlaneTokenExchangeResponse>(
                    url = "${baseUrl()}/api/access_tokens",
                    jsonBody = "{}",
                    bearerToken = "cp_token"
                )
            }

            assertEquals("dp_token_xyz", result.token)
            assertNull(result.error)
        }

        @Test
        fun `parses error response`() {
            wireMockServer.stubFor(
                WireMock.post("/api/access_tokens")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"error": {"code": 401, "message": "Unauthorized"}}""")
                    )
            )

            val result = runBlocking {
                CCloudOAuthHttpClient.postJson<DataPlaneTokenExchangeResponse>(
                    url = "${baseUrl()}/api/access_tokens",
                    jsonBody = "{}",
                    bearerToken = "invalid_token"
                )
            }

            assertNull(result.token)
            assertTrue(result.error.toString().contains("401"))
            assertTrue(result.error.toString().contains("Unauthorized"))
        }

        @Test
        fun `parses 400 error response from errorStream`() {
            wireMockServer.stubFor(
                WireMock.post("/api/access_tokens")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(400)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"error": {"code": 400, "message": "Invalid request"}}""")
                    )
            )

            val result = runBlocking {
                CCloudOAuthHttpClient.postJson<DataPlaneTokenExchangeResponse>(
                    url = "${baseUrl()}/api/access_tokens",
                    jsonBody = "{}",
                    bearerToken = "token"
                )
            }

            assertNull(result.token)
            assertTrue(result.error.toString().contains("400"))
            assertTrue(result.error.toString().contains("Invalid request"))
        }

        @Test
        fun `throws HttpStatusException on 500 server error`() {
            wireMockServer.stubFor(
                WireMock.post("/api/access_tokens")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(503)
                            .withBody("Service Unavailable")
                    )
            )

            val exception = assertThrows(HttpRequests.HttpStatusException::class.java) {
                runBlocking {
                    CCloudOAuthHttpClient.postJson<DataPlaneTokenExchangeResponse>(
                        url = "${baseUrl()}/api/access_tokens",
                        jsonBody = "{}",
                        bearerToken = "token"
                    )
                }
            }

            assertEquals(503, exception.statusCode)
            assertTrue(exception.message!!.contains("Service Unavailable"))
        }
    }

    @Nested
    @DisplayName("get")
    inner class Get {

        @Test
        fun `sends get with bearer token`() {
            wireMockServer.stubFor(
                WireMock.get("/api/check_jwt")
                    .withHeader("Authorization", WireMock.equalTo("Bearer valid_token"))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"claims": {"prop": "user-123", "email": "test@example.com"}}""")
                    )
            )

            val result = runBlocking {
                CCloudOAuthHttpClient.get<CheckJwtResponse>(
                    url = "${baseUrl()}/api/check_jwt",
                    bearerToken = "valid_token"
                )
            }

            assertNull(result.error)
            assertTrue(result.claims.toString().contains("user-123"))
            assertTrue(result.claims.toString().contains("test@example.com"))
        }

        @Test
        fun `parses error response`() {
            wireMockServer.stubFor(
                WireMock.get("/api/check_jwt")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"error": {"code": 401, "message": "Token expired"}}""")
                    )
            )

            val result = runBlocking {
                CCloudOAuthHttpClient.get<CheckJwtResponse>(
                    url = "${baseUrl()}/api/check_jwt",
                    bearerToken = "expired_token"
                )
            }

            assertTrue(result.error.toString().contains("401"))
            assertTrue(result.error.toString().contains("Token expired"))
            assertNull(result.claims)
        }

        @Test
        fun `parses 400 error response from errorStream`() {
            wireMockServer.stubFor(
                WireMock.get("/api/check_jwt")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(400)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"error": {"code": 400, "message": "Missing token"}}""")
                    )
            )

            val result = runBlocking {
                CCloudOAuthHttpClient.get<CheckJwtResponse>(
                    url = "${baseUrl()}/api/check_jwt",
                    bearerToken = null
                )
            }

            assertTrue(result.error.toString().contains("400"))
            assertTrue(result.error.toString().contains("Missing token"))
            assertNull(result.claims)
        }

        @Test
        fun `throws HttpStatusException on 500 server error`() {
            wireMockServer.stubFor(
                WireMock.get("/api/check_jwt")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(502)
                            .withBody("Bad Gateway")
                    )
            )

            val exception = assertThrows(HttpRequests.HttpStatusException::class.java) {
                runBlocking {
                    CCloudOAuthHttpClient.get<CheckJwtResponse>(
                        url = "${baseUrl()}/api/check_jwt",
                        bearerToken = "token"
                    )
                }
            }

            assertEquals(502, exception.statusCode)
            assertTrue(exception.message!!.contains("Bad Gateway"))
        }
    }
}
