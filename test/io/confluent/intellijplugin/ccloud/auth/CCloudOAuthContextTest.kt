package io.confluent.intellijplugin.ccloud.auth

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.ccloud.util.ResourceLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@TestApplication
class CCloudOAuthContextTest {

    companion object {
        private lateinit var wireMockServer: WireMockServer
        private const val MOCK_AUTH_CODE = "test_authorization_code"
        private const val MOCK_REFRESH_TOKEN = "test_refresh_token"
        private const val MOCK_ID_TOKEN = "test_id_token"
        private const val MOCK_CONTROL_PLANE_TOKEN = "test_cp_token"
        private const val MOCK_DATA_PLANE_TOKEN = "test_dp_token"
        private const val MOCK_ORG_ID = "org-123"

        @JvmStatic
        @BeforeAll
        fun setUpAll() {
            wireMockServer = WireMockServer(wireMockConfig().dynamicPort())
            wireMockServer.start()

            val baseUrl = "http://localhost:${wireMockServer.port()}"
            System.setProperty("ccloud.oauth.token-uri", "$baseUrl/oauth/token")
            System.setProperty("ccloud.control-plane.token-exchange-uri", "$baseUrl/api/sessions")
            System.setProperty("ccloud.control-plane.check-jwt-uri", "$baseUrl/api/check_jwt")
            System.setProperty("ccloud.data-plane.token-exchange-uri", "$baseUrl/api/access_tokens")
        }

        @JvmStatic
        @AfterAll
        fun tearDownAll() {
            wireMockServer.stop()
            System.clearProperty("ccloud.oauth.token-uri")
            System.clearProperty("ccloud.control-plane.token-exchange-uri")
            System.clearProperty("ccloud.control-plane.check-jwt-uri")
            System.clearProperty("ccloud.data-plane.token-exchange-uri")
        }
    }

    private lateinit var context: CCloudOAuthContext

    @BeforeEach
    fun setUp() {
        context = CCloudOAuthContext()
        registerWireMockRoutesForCCloudOAuth()
    }

    @AfterEach
    fun cleanUp() {
        wireMockServer.resetAll()
    }

    private fun registerWireMockRoutesForCCloudOAuth() {
        // ID token exchange (authorization code -> ID token)
        wireMockServer.stubFor(
            WireMock.post("/oauth/token")
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {
                                "access_token": "test_access_token",
                                "refresh_token": "$MOCK_REFRESH_TOKEN",
                                "id_token": "$MOCK_ID_TOKEN",
                                "token_type": "Bearer",
                                "expires_in": 86400
                            }
                            """.trimIndent()
                        )
                )
        )

        // Control plane token exchange
        wireMockServer.stubFor(
            WireMock.post("/api/sessions")
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Set-Cookie", "auth_token=$MOCK_CONTROL_PLANE_TOKEN; Path=/; HttpOnly")
                        .withBody(
                            """
                            {
                                "user": {
                                    "id": "user-123",
                                    "email": "test@example.com",
                                    "first_name": "Test",
                                    "last_name": "User"
                                },
                                "organization": {
                                    "id": "org-123",
                                    "name": "Test Org",
                                    "resource_id": "org-res-123",
                                    "sso": {
                                        "enabled": false,
                                        "mode": "none",
                                        "vendor": "none"
                                    }
                                }
                            }
                            """.trimIndent()
                        )
                )
        )

        // Data plane token exchange
        wireMockServer.stubFor(
            WireMock.post("/api/access_tokens")
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"token": "$MOCK_DATA_PLANE_TOKEN"}""")
                )
        )

        // Check JWT endpoint
        wireMockServer.stubFor(
            WireMock.get("/api/check_jwt")
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"claims": {"sub": "user-123"}}""")
                )
        )
    }

    @Nested
    @DisplayName("initialization")
    inner class Initialization {

        @Test
        fun `should initialize the oauth state`() {
            assertFalse(context.oauthState.isEmpty())
        }

        @Test
        fun `should assign unique oauth states`() {
            val firstContext = CCloudOAuthContext()
            val secondContext = CCloudOAuthContext()
            val thirdContext = CCloudOAuthContext()

            assertNotEquals(firstContext.oauthState, secondContext.oauthState)
            assertNotEquals(firstContext.oauthState, thirdContext.oauthState)
            assertNotEquals(secondContext.oauthState, thirdContext.oauthState)
        }

        @Test
        fun `should generate unique code verifier`() {
            val firstContext = CCloudOAuthContext()
            val secondContext = CCloudOAuthContext()

            assertNotEquals(firstContext.codeVerifier, secondContext.codeVerifier)
            assertTrue(firstContext.codeVerifier.isNotEmpty())
        }

        @Test
        fun `code challenge should be non-empty and different from code verifier`() {
            assertTrue(context.codeChallenge.isNotEmpty())
            assertNotEquals(context.codeVerifier, context.codeChallenge)
        }

        @Test
        fun `initial state should have no tokens`() {
            assertNull(context.getRefreshToken())
            assertNull(context.getControlPlaneToken())
            assertNull(context.getDataPlaneToken())
            assertNull(context.getUser())
            assertNull(context.getCurrentOrganization())
        }
    }

    @Nested
    @DisplayName("getSignInUri")
    inner class GetSignInUri {

        @Test
        fun `should return a valid uri with required OAuth parameters`() {
            val signInUri = context.getSignInUri()

            assertTrue(signInUri.contains("response_type=code"))
            assertTrue(signInUri.contains("code_challenge_method=S256"))
            assertTrue(signInUri.contains("code_challenge="))
            assertTrue(signInUri.contains("client_id="))
            assertTrue(signInUri.contains("redirect_uri="))
            assertTrue(signInUri.contains("scope="))
            assertTrue(signInUri.contains("state=${context.oauthState}"))
        }
    }

    @Nested
    @DisplayName("getUserEmail")
    inner class GetUserEmail {

        @Test
        fun `should return placeholder if not authenticated`() {
            assertEquals("UNKNOWN", context.getUserEmail())
        }

        @Test
        fun `should return the principal's email if authenticated`() {
            runBlocking {
                context.createTokensFromAuthorizationCode(MOCK_AUTH_CODE, MOCK_ORG_ID)
            }

            assertEquals("test@example.com", context.getUserEmail())
        }
    }

    @Nested
    @DisplayName("expiresAt")
    inner class ExpiresAt {

        @Test
        fun `should return null if all tokens are missing`() {
            assertNull(context.expiresAt())
        }

        @Test
        fun `should return the earliest expiration time`() {
            runBlocking {
                context.createTokensFromAuthorizationCode(MOCK_AUTH_CODE, MOCK_ORG_ID)
            }

            val expiresAt = context.expiresAt()
            assertNotNull(expiresAt)
            // Control plane token should expire first (5 minutes)
            assertEquals(context.getControlPlaneToken()?.expiresAt, expiresAt)
        }
    }

    @Nested
    @DisplayName("hasReachedEndOfLifetime")
    inner class HasReachedEndOfLifetime {

        @Test
        fun `should return false if end of lifetime is null`() {
            assertFalse(context.hasReachedEndOfLifetime())
        }

        @Test
        fun `should return false if end of lifetime is not reached`() {
            runBlocking {
                context.createTokensFromAuthorizationCode(MOCK_AUTH_CODE, MOCK_ORG_ID)
            }

            assertFalse(context.hasReachedEndOfLifetime())
        }

        @Test
        fun `should return true if end of lifetime has been reached`() {
            // Set absolute lifetime to 0 seconds so it expires immediately
            System.setProperty("ccloud.refresh-token.absolute-lifetime-seconds", "0")
            try {
                runBlocking {
                    context.createTokensFromAuthorizationCode(MOCK_AUTH_CODE, MOCK_ORG_ID)
                }

                assertTrue(context.hasReachedEndOfLifetime())
            } finally {
                System.clearProperty("ccloud.refresh-token.absolute-lifetime-seconds")
            }
        }
    }

    @Nested
    @DisplayName("shouldAttemptTokenRefresh")
    inner class ShouldAttemptTokenRefresh {

        @Test
        fun `should return false if refresh token is missing`() {
            assertFalse(context.shouldAttemptTokenRefresh())
        }

        @Test
        fun `should return false when tokens are fresh`() {
            // Fresh tokens expire in 5 minutes, check interval is 5 seconds
            // Since 5 min >> 5 sec, no refresh needed
            runBlocking {
                context.createTokensFromAuthorizationCode(MOCK_AUTH_CODE, MOCK_ORG_ID)
            }

            assertFalse(context.shouldAttemptTokenRefresh())
        }

        @Test
        fun `should return true when token expires before next check interval`() {
            // Set control plane token lifetime to 1 second (less than 5 second check interval)
            System.setProperty("ccloud.control-plane.token-lifetime-seconds", "1")
            try {
                runBlocking {
                    context.createTokensFromAuthorizationCode(MOCK_AUTH_CODE, MOCK_ORG_ID)
                }

                // Token expires in 1 second, check interval is 5 seconds
                // Since 1 sec < 5 sec, refresh IS needed
                assertTrue(context.shouldAttemptTokenRefresh())
            } finally {
                System.clearProperty("ccloud.control-plane.token-lifetime-seconds")
            }
        }
    }

    @Nested
    @DisplayName("createTokensFromAuthorizationCode")
    inner class CreateTokensFromAuthorizationCode {

        @Test
        fun `should exchange control and data plane tokens`() {
            val result = runBlocking {
                context.createTokensFromAuthorizationCode(MOCK_AUTH_CODE, MOCK_ORG_ID)
            }

            assertTrue(result.isSuccess)
            assertNotNull(context.getControlPlaneToken())
            assertNotNull(context.getDataPlaneToken())
            assertNotNull(context.getCurrentOrganization())
            assertEquals("org-123", context.getCurrentOrganization()?.id)
            assertEquals("user-123", context.getUser()?.id)
            // Error related to sign in should not be present
            assertNull(context.getErrors().signIn)
        }

        @Test
        fun `should return failed result if token exchange returns error`() {
            wireMockServer.stubFor(
                WireMock.post("/oauth/token")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"error": "invalid_request", "error_description": "Mock error response"}""")
                    ).atPriority(1)
            )

            val result = runBlocking {
                context.createTokensFromAuthorizationCode(MOCK_AUTH_CODE, MOCK_ORG_ID)
            }

            assertTrue(result.isFailure)
            // Error related to sign in should be present
            assertNotNull(context.getErrors().signIn)
        }

        @Test
        fun `should return failed result if control plane token exchange fails`() {
            wireMockServer.stubFor(
                WireMock.post("/api/sessions")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withHeader("Set-Cookie", "auth_token=bad_token")
                            .withBody("""{"error":{"code":401,"message":"Unauthorized"}}""")
                    ).atPriority(1)
            )

            val result = runBlocking {
                context.createTokensFromAuthorizationCode(MOCK_AUTH_CODE, MOCK_ORG_ID)
            }

            assertTrue(result.isFailure)
            // Error related to sign in should be present
            assertNotNull(context.getErrors().signIn)
        }

        @Test
        fun `should return failed result if cookie header is not present`() {
            wireMockServer.stubFor(
                WireMock.post("/api/sessions")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{}")
                    ).atPriority(1)
            )

            val result = runBlocking {
                context.createTokensFromAuthorizationCode(MOCK_AUTH_CODE, MOCK_ORG_ID)
            }

            assertTrue(result.isFailure)
            assertNotNull(context.getErrors().signIn)
        }

        @Test
        fun `should return failed result if data plane token exchange fails`() {
            wireMockServer.stubFor(
                WireMock.post("/api/access_tokens")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"error":{"code":401,"message":"Unauthorized"}}""")
                    ).atPriority(1)
            )

            val result = runBlocking {
                context.createTokensFromAuthorizationCode(MOCK_AUTH_CODE, MOCK_ORG_ID)
            }

            assertTrue(result.isFailure)
            // Error related to sign in should be present
            assertNotNull(context.getErrors().signIn)
        }
    }

    @Nested
    @DisplayName("refresh")
    inner class Refresh {

        @BeforeEach
        fun setUpRefresh() {
            runBlocking {
                context.createTokensFromAuthorizationCode(MOCK_AUTH_CODE, MOCK_ORG_ID)
            }
        }

        @Test
        fun `should perform the token exchange with a refresh token`() {
            // Capture old token state
            val oldExpiresAt = context.expiresAt()

            val result = runBlocking {
                context.refresh(MOCK_ORG_ID)
            }

            assertTrue(result.isSuccess)
            // Verify tokens exist
            assertNotNull(context.getRefreshToken())
            assertNotNull(context.getControlPlaneToken())
            assertNotNull(context.getDataPlaneToken())
            // Verify expiration was updated (tokens were refreshed)
            assertNotNull(oldExpiresAt)
            assertTrue(context.expiresAt()!! > oldExpiresAt)
            // Error related to token refresh should not be present
            assertNull(context.getErrors().tokenRefresh)
        }

        @Test
        fun `should return failed result if the refresh token is absent`() {
            context.reset()

            val result = runBlocking {
                context.refresh(MOCK_ORG_ID)
            }

            assertTrue(result.isFailure)
            // Error related to token refresh should be present
            assertNotNull(context.getErrors().tokenRefresh)
        }

        @Test
        fun `should track failed attempts on failure`() {
            wireMockServer.stubFor(
                WireMock.post("/oauth/token")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"error": "invalid_grant", "error_description": "Refresh token expired"}""")
                    ).atPriority(1)
            )

            runBlocking {
                context.refresh(MOCK_ORG_ID)
            }

            assertEquals(1, context.getFailedTokenRefreshAttempts())
        }

        @Test
        fun `should increment failed attempts on consecutive failures`() {
            wireMockServer.stubFor(
                WireMock.post("/oauth/token")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"error": "invalid_grant", "error_description": "Refresh token expired"}""")
                    ).atPriority(1)
            )

            runBlocking {
                context.refresh(MOCK_ORG_ID)
                context.refresh(MOCK_ORG_ID)
                context.refresh(MOCK_ORG_ID)
            }

            assertEquals(3, context.getFailedTokenRefreshAttempts())
        }

        @Test
        fun `should reset failed attempts counter on success`() {
            // Cause some failed attempts
            wireMockServer.stubFor(
                WireMock.post("/oauth/token")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"error": "invalid_grant", "error_description": "Refresh token expired"}""")
                    ).atPriority(1)
            )

            runBlocking {
                context.refresh(MOCK_ORG_ID)
                context.refresh(MOCK_ORG_ID)
            }

            assertEquals(2, context.getFailedTokenRefreshAttempts())

            // Restore the successful stub and verify counter resets
            wireMockServer.resetAll()
            registerWireMockRoutesForCCloudOAuth()

            val result = runBlocking {
                context.refresh(MOCK_ORG_ID)
            }

            assertTrue(result.isSuccess)
            assertEquals(0, context.getFailedTokenRefreshAttempts())
        }
    }

    @Nested
    @DisplayName("refresh endpoint errors")
    inner class RefreshEndpointErrors {

        @BeforeEach
        fun setUpRefresh() {
            runBlocking {
                context.createTokensFromAuthorizationCode(MOCK_AUTH_CODE, MOCK_ORG_ID)
            }
        }

        private fun stubFailure(path: String, status: Int, fixture: String? = null, retryAfter: String? = null) {
            val response = WireMock.aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/json")
            fixture?.let { response.withBody(ResourceLoader.loadResource("fixtures/ccloud-auth/$it")) }
            retryAfter?.let { response.withHeader("Retry-After", it) }
            wireMockServer.stubFor(WireMock.post(path).willReturn(response).atPriority(1))
        }

        private fun refreshFailure(): Throwable =
            runBlocking { context.refresh(MOCK_ORG_ID) }.exceptionOrNull()
                ?: error("Expected refresh to fail")

        @Test
        fun `invalid refresh token on oauth token keeps status and is terminal`() {
            stubFailure("/oauth/token", 403, "oauth-token-invalid-refresh-token.json")

            val error = refreshFailure()

            assertTrue(error is OAuthErrorException)
            error as OAuthErrorException
            assertEquals(403, error.httpStatusCode)
            assertEquals("invalid_grant", error.errorCode)
            assertEquals(TokenEndpoint.OAUTH_TOKEN, error.endpoint)
            assertTrue(context.hasNonTransientError())
            assertEquals(1, context.getFailedTokenRefreshAttempts())
        }

        @Test
        fun `4xx with a non-JSON body on oauth token is typed and terminal`() {
            wireMockServer.stubFor(
                WireMock.post("/oauth/token")
                    .willReturn(WireMock.aResponse().withStatus(401).withBody("Unauthorized"))
                    .atPriority(1)
            )

            val error = refreshFailure()

            assertTrue(error is TokenEndpointException)
            assertEquals(401, (error as TokenEndpointException).httpStatusCode)
            assertTrue(context.hasNonTransientError())
        }

        @Test
        fun `401 from control plane is typed and terminal`() {
            stubFailure("/api/sessions", 401, "control-plane-unauthorized.json")

            val error = refreshFailure()

            assertTrue(error is TokenEndpointException)
            error as TokenEndpointException
            assertEquals(TokenEndpoint.CONTROL_PLANE, error.endpoint)
            assertEquals(401, error.httpStatusCode)
            assertTrue(context.hasNonTransientError())
        }

        @Test
        fun `403 from data plane is typed and terminal`() {
            stubFailure("/api/access_tokens", 403, "data-plane-forbidden.json")

            val error = refreshFailure()

            assertTrue(error is TokenEndpointException)
            error as TokenEndpointException
            assertEquals(TokenEndpoint.DATA_PLANE, error.endpoint)
            assertEquals(403, error.httpStatusCode)
            assertTrue(context.hasNonTransientError())
        }

        @Test
        fun `429 keeps the Retry-After header and stays transient`() {
            stubFailure("/oauth/token", 429, "oauth-token-rate-limited.json", retryAfter = "30")

            val error = refreshFailure()

            assertTrue(error is OAuthErrorException)
            error as OAuthErrorException
            assertEquals(429, error.httpStatusCode)
            assertEquals("30", error.retryAfterHeader)
            assertFalse(context.hasNonTransientError())
        }

        @Test
        fun `429 from data plane keeps the Retry-After header`() {
            stubFailure("/api/access_tokens", 429, retryAfter = "12")

            val error = refreshFailure()

            assertTrue(error is TokenEndpointException)
            assertEquals("12", (error as TokenEndpointException).retryAfterHeader)
            assertFalse(context.hasNonTransientError())
        }

        @Test
        fun `408 from control plane stays transient`() {
            stubFailure("/api/sessions", 408)

            refreshFailure()

            assertFalse(context.hasNonTransientError())
        }

        @Test
        fun `5xx is typed with its step and status and stays transient`() {
            stubFailure("/api/sessions", 503)

            val error = refreshFailure()

            assertTrue(error is TokenEndpointException)
            error as TokenEndpointException
            assertEquals(TokenEndpoint.CONTROL_PLANE, error.endpoint)
            assertEquals(503, error.httpStatusCode)
            assertFalse(context.hasNonTransientError())
        }

        @Test
        fun `missing refresh token is terminal`() {
            context.reset()

            val error = refreshFailure()

            assertTrue(error is MissingRefreshTokenException)
            assertTrue(context.hasNonTransientError())
        }
    }

    @Nested
    @DisplayName("concurrency")
    inner class Concurrency {

        private val concurrentCalls = 8
        private val oauthDelayMs = 50

        @BeforeEach
        fun setUpSignedIn() {
            runBlocking {
                context.createTokensFromAuthorizationCode(MOCK_AUTH_CODE, MOCK_ORG_ID)
            }
            // Slow oauth/token so concurrent callers really overlap while one holds the lock
            wireMockServer.stubFor(
                WireMock.post("/oauth/token")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withFixedDelay(oauthDelayMs)
                            .withBody(ResourceLoader.loadResource("fixtures/ccloud-auth/oauth-token-success.json"))
                    ).atPriority(1)
            )
        }

        @Test
        fun `concurrent refreshes from different threads all complete without lock errors`() {
            // The HTTP calls hop to Dispatchers.IO, so each refresh resumes on a different thread
            // than it started on. A thread-owned lock fails here (IllegalMonitorStateException on unlock).
            val results = runBlocking {
                withTimeout(30.seconds) {
                    (1..concurrentCalls).map { i ->
                        val dispatcher = if (i % 2 == 0) Dispatchers.Default else Dispatchers.IO
                        async(dispatcher) { runCatching { context.refresh(MOCK_ORG_ID) } }
                    }.awaitAll()
                }
            }

            results.forEach { outer ->
                assertTrue(outer.isSuccess, "refresh threw: ${outer.exceptionOrNull()}")
                assertTrue(outer.getOrThrow().isSuccess, "refresh failed: ${outer.getOrThrow().exceptionOrNull()}")
            }
            assertEquals(0, context.getFailedTokenRefreshAttempts())
        }

        @Test
        fun `concurrent refreshes run one at a time`() {
            val elapsedMs = runBlocking {
                val start = System.nanoTime()
                (1..concurrentCalls).map {
                    async(Dispatchers.Default) { context.refresh(MOCK_ORG_ID) }
                }.awaitAll()
                (System.nanoTime() - start) / 1_000_000
            }

            // Serialized: every refresh waits for the previous one's slow oauth/token call
            assertTrue(
                elapsedMs >= concurrentCalls * oauthDelayMs,
                "expected >= ${concurrentCalls * oauthDelayMs}ms, took ${elapsedMs}ms"
            )
        }

        @Test
        fun `cancelled sign-in records no sign-in error and releases the lock`() {
            wireMockServer.resetRequests()
            val result = runBlocking {
                val signIn = launch(Dispatchers.Default) {
                    context.createTokensFromAuthorizationCode(MOCK_AUTH_CODE, MOCK_ORG_ID)
                }
                awaitOAuthTokenRequest() // sign-in is now inside its slow oauth/token call
                signIn.cancelAndJoin()
                assertTrue(signIn.isCancelled)

                // Cancellation is not a sign-in failure
                assertNull(context.getErrors().signIn)
                assertFalse(context.hasNonTransientError())

                // The Mutex was released: the next caller gets in
                withTimeout(10.seconds) { context.createTokensFromAuthorizationCode(MOCK_AUTH_CODE, MOCK_ORG_ID) }
            }

            assertTrue(result.isSuccess, "sign-in after cancelled sign-in failed: ${result.exceptionOrNull()}")
        }

        @Test
        fun `cancelled refresh does not count as a failed attempt`() {
            wireMockServer.resetRequests()
            runBlocking {
                val refresh = launch(Dispatchers.Default) { context.refresh(MOCK_ORG_ID) }
                awaitOAuthTokenRequest()
                refresh.cancelAndJoin()
            }

            assertEquals(0, context.getFailedTokenRefreshAttempts())
            assertNull(context.getErrors().tokenRefresh)
        }

        private suspend fun awaitOAuthTokenRequest() = withTimeout(10.seconds) {
            while (wireMockServer.countRequestsMatching(
                    WireMock.postRequestedFor(WireMock.urlEqualTo("/oauth/token")).build()
                ).count == 0
            ) {
                delay(5)
            }
        }

        @Test
        fun `sign-in and refresh do not interleave`() {
            val results = runBlocking {
                listOf(
                    async(Dispatchers.Default) { context.createTokensFromAuthorizationCode(MOCK_AUTH_CODE, MOCK_ORG_ID) },
                    async(Dispatchers.IO) { context.refresh(MOCK_ORG_ID) },
                    async(Dispatchers.Default) { context.checkAuthenticationStatus() },
                ).awaitAll()
            }

            results.forEach { assertTrue(it.isSuccess, "call failed: ${it.exceptionOrNull()}") }
        }
    }

    @Nested
    @DisplayName("refreshIgnoreFailures")
    inner class RefreshIgnoreFailures {

        @BeforeEach
        fun setUpRefresh() {
            runBlocking {
                context.createTokensFromAuthorizationCode(MOCK_AUTH_CODE, MOCK_ORG_ID)
            }
        }

        @Test
        fun `should not track failures`() {
            wireMockServer.stubFor(
                WireMock.post("/oauth/token")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"error": "invalid_grant", "error_description": "Refresh token expired"}""")
                    ).atPriority(1)
            )

            runBlocking {
                context.refreshIgnoreFailures(MOCK_ORG_ID)
                context.refreshIgnoreFailures(MOCK_ORG_ID)
            }

            // Should not track failures
            assertNull(context.getErrors().tokenRefresh)
        }

        @Test
        fun `should reset counter on success`() {
            val result = runBlocking {
                context.refreshIgnoreFailures(MOCK_ORG_ID)
            }

            assertTrue(result.isSuccess)
            assertEquals(0, context.getFailedTokenRefreshAttempts())
        }
    }

    @Nested
    @DisplayName("checkAuthenticationStatus")
    inner class CheckAuthenticationStatus {

        @Test
        fun `should return false if the control plane token is absent`() {
            val result = runBlocking {
                context.checkAuthenticationStatus()
            }

            assertTrue(result.isFailure)
            // Error related to auth status check should be present
            assertNotNull(context.getErrors().authStatusCheck)
        }

        @Test
        fun `should return true if the control plane token is valid`() {
            runBlocking {
                context.createTokensFromAuthorizationCode(MOCK_AUTH_CODE, MOCK_ORG_ID)
            }

            val result = runBlocking {
                context.checkAuthenticationStatus()
            }

            assertTrue(result.isSuccess)
            assertTrue(result.getOrThrow())
            // Error related to auth status check should not be present
            assertNull(context.getErrors().authStatusCheck)
        }

        @Test
        fun `should return failure if CCloud returns an error`() {
            runBlocking {
                context.createTokensFromAuthorizationCode(MOCK_AUTH_CODE, MOCK_ORG_ID)
            }

            wireMockServer.stubFor(
                WireMock.get("/api/check_jwt")
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""{"error":{"code":401,"message":"Unauthorized"}}""")
                    ).atPriority(1)
            )

            val result = runBlocking {
                context.checkAuthenticationStatus()
            }

            assertTrue(result.isFailure)
            assertNotNull(context.getErrors().authStatusCheck)
        }
    }

    @Nested
    @DisplayName("reset")
    inner class Reset {

        @Test
        fun `should void all tokens and errors`() {
            runBlocking {
                context.createTokensFromAuthorizationCode(MOCK_AUTH_CODE, MOCK_ORG_ID)
            }

            // Make sure that we got some tokens
            assertNotNull(context.getControlPlaneToken())
            assertNotNull(context.getDataPlaneToken())
            // The auth context will expire at some point
            assertNotNull(context.expiresAt())

            context.reset()

            // Neither errors nor failed token refresh attempts
            assertEquals(AuthErrors(), context.getErrors())
            assertEquals(0, context.getFailedTokenRefreshAttempts())
            // All tokens are gone
            assertNull(context.getRefreshToken())
            assertNull(context.getControlPlaneToken())
            assertNull(context.getDataPlaneToken())
            // The auth context will not expire because it does not hold any tokens
            assertNull(context.expiresAt())
        }
    }

    @Nested
    @DisplayName("getErrors")
    inner class GetErrors {

        @Test
        fun `should return empty errors initially`() {
            assertFalse(context.getErrors().hasErrors())
        }

        @Test
        fun `hasNonTransientError should return false initially`() {
            assertFalse(context.hasNonTransientError())
        }
    }
}
