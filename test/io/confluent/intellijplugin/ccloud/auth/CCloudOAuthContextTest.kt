package io.confluent.intellijplugin.ccloud.auth

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
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
        fun `code challenge should be derived from code verifier`() {
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
            val result = runBlocking {
                context.refresh(MOCK_ORG_ID)
            }

            assertTrue(result.isSuccess)
            assertEquals(0, context.getFailedTokenRefreshAttempts())
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
