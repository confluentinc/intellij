package io.confluent.intellijplugin.ccloud.auth

import kotlinx.serialization.json.JsonNull
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * OAuth-based authentication flow with Confluent Cloud API.
 * Manages refresh token, control plane token, and data plane token.
 */
class CCloudOAuthContext {

    val oauthState: String = randomBase64(OAUTH_STATE_LENGTH)
    val codeVerifier: String = randomBase64(CODE_VERIFIER_LENGTH)
    val codeChallenge: String = sha256Base64(codeVerifier)

    companion object {
        private const val CODE_VERIFIER_LENGTH = 32
        private const val OAUTH_STATE_LENGTH = 32
        private const val UNKNOWN_EMAIL = "UNKNOWN"

        private val secureRandom = SecureRandom()
        private val base64Encoder = Base64.getUrlEncoder().withoutPadding()

        private fun randomBase64(bytes: Int): String =
            ByteArray(bytes).also { secureRandom.nextBytes(it) }.let { base64Encoder.encodeToString(it) }

        private fun sha256Base64(input: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(input.toByteArray(Charsets.US_ASCII))
                .let { base64Encoder.encodeToString(it) }
    }

    private val tokens = AtomicReference(Tokens())
    private val readWriteLock = ReentrantReadWriteLock()
    private val writeLock = readWriteLock.writeLock()
    private val readLock = readWriteLock.readLock()

    // Public accessors
    fun getRefreshToken(): Token? = tokens.get().refreshToken
    fun getControlPlaneToken(): Token? = tokens.get().controlPlaneToken
    fun getDataPlaneToken(): Token? = tokens.get().dataPlaneToken
    fun getEndOfLifetime(): Instant? = tokens.get().endOfLifetime
    fun getUser(): UserDetails? = tokens.get().user
    fun getCurrentOrganization(): OrganizationDetails? = tokens.get().organization
    fun getErrors(): AuthErrors = tokens.get().errors
    fun getFailedTokenRefreshAttempts(): Int = tokens.get().failedTokenRefreshAttempts

    fun getUserEmail(): String = getUser()?.email ?: UNKNOWN_EMAIL

    // Earliest token expiry, used to schedule refresh
    fun expiresAt(): Instant? {
        readLock.lock()
        try {
            return tokens.get().expiresAt()
        } finally {
            readLock.unlock()
        }
    }

    fun hasReachedEndOfLifetime(): Boolean {
        val endOfLifetime = getEndOfLifetime() ?: return false
        return Instant.now() >= endOfLifetime
    }

    fun hasNonTransientError(): Boolean = tokens.get().errors.hasNonTransientErrors()

    // Check if we should attempt token refresh
    fun shouldAttemptTokenRefresh(): Boolean {
        readLock.lock()
        try {
            val now = Instant.now()
            val expiresAt = expiresAt() ?: return false
            val nextExecution = now.plusSeconds(CCloudOAuthConfig.CHECK_TOKEN_EXPIRATION_INTERVAL.inWholeSeconds)
            val atLeastOneTokenWillExpireBeforeNextRun = expiresAt < nextExecution

            return !hasReachedEndOfLifetime()
                    && !hasNonTransientError()
                    && atLeastOneTokenWillExpireBeforeNextRun
        } finally {
            readLock.unlock()
        }
    }

    fun getSignInUri(): String = buildString {
        append(CCloudOAuthConfig.CCLOUD_OAUTH_AUTHORIZE_URI)
        append("?response_type=code")
        append("&code_challenge_method=S256")
        append("&code_challenge=$codeChallenge")
        append("&state=$oauthState")
        append("&client_id=${CCloudOAuthConfig.CCLOUD_OAUTH_CLIENT_ID}")
        append("&redirect_uri=${encode(CCloudOAuthConfig.CCLOUD_OAUTH_REDIRECT_URI)}")
        append("&scope=${encode(CCloudOAuthConfig.CCLOUD_OAUTH_SCOPE)}")
    }

    // Token Exchange Methods

    /** Create tokens from authorization code (after OAuth callback). */
    suspend fun createTokensFromAuthorizationCode(
        authorizationCode: String,
        organizationId: String? = null
    ): Result<CCloudOAuthContext> {
        writeLock.lock()
        try {
            return runCatching {
                // Set absolute lifetime on fresh sign-in
                tokens.updateAndGet { oldTokens ->
                    oldTokens.withEndOfLifetime(
                        Instant.now().plusSeconds(CCloudOAuthConfig.CCLOUD_REFRESH_TOKEN_ABSOLUTE_LIFETIME.inWholeSeconds)
                    )
                }

                val idTokenResponse = exchangeAuthorizationCode(authorizationCode)
                processTokenExchangeResponse(idTokenResponse, organizationId)
            }.onSuccess {
                // Reset any existing errors
                tokens.updateAndGet { oldTokens ->
                    oldTokens.withErrors(
                        oldTokens.errors
                            .withoutSignIn()
                            .withoutTokenRefresh()
                            .withoutAuthStatusCheck()
                    )
                }
            }.onFailure { failure ->
                tokens.updateAndGet { oldTokens ->
                    oldTokens.withErrors(oldTokens.errors.withSignIn(failure.message ?: "Unknown error"))
                }
            }.map { this }
        } finally {
            writeLock.unlock()
        }
    }

    /**
     * Refresh all tokens using refresh_token.
     * Called periodically by background refresh job - TODO
     */
    suspend fun refresh(organizationId: String? = null): Result<CCloudOAuthContext> {
        writeLock.lock()
        try {
            return runCatching {
                val idTokenResponse = createTokensFromRefreshToken()
                processTokenExchangeResponse(idTokenResponse, organizationId)
            }.onSuccess {
                // Reset failed attempts counter on success
                tokens.updateAndGet { it.withSuccessfulTokenRefreshAttempt() }
            }.onFailure { failure ->
                // Track failed attempt - becomes non-transient after MAX_TOKEN_REFRESH_ATTEMPTS
                tokens.updateAndGet { it.withFailedTokenRefreshAttempt(failure, CCloudOAuthConfig.MAX_TOKEN_REFRESH_ATTEMPTS) }
            }.map { this }
        } finally {
            writeLock.unlock()
        }
    }

    suspend fun refreshIgnoreFailures(organizationId: String? = null): Result<CCloudOAuthContext> {
        writeLock.lock()
        try {
            return runCatching {
                val idTokenResponse = createTokensFromRefreshToken()
                processTokenExchangeResponse(idTokenResponse, organizationId)
            }.onSuccess {
                tokens.updateAndGet { it.withSuccessfulTokenRefreshAttempt() }
            }.map { this }
            // Note: failures are NOT tracked
        } finally {
            writeLock.unlock()
        }
    }

    suspend fun checkAuthenticationStatus(): Result<Boolean> {
        writeLock.lock()
        try {
            val controlPlaneToken = tokens.get().controlPlaneToken
            if (isTokenMissing(controlPlaneToken)) {
                val errorMessage = "Cannot verify authentication status: no control plane token available."
                tokens.updateAndGet { it.withErrors(it.errors.withAuthStatusCheck(errorMessage)) }
                return Result.failure(IllegalStateException(errorMessage))
            }

            return runCatching {
                // TODO: HTTP GET to CCLOUD_CONTROL_PLANE_CHECK_JWT_URI
                // Headers: Authorization: Bearer {controlPlaneToken}
                // Response: CheckJwtResponse - if error field is non-null, token is invalid
                TODO("Implement HTTP call to check_jwt endpoint")
            }.onSuccess {
                tokens.updateAndGet { it.withErrors(it.errors.withoutAuthStatusCheck()) }
            }.onFailure { failure ->
                tokens.updateAndGet { it.withErrors(it.errors.withAuthStatusCheck(failure.message ?: "Unknown error")) }
            }
        } finally {
            writeLock.unlock()
        }
    }

    fun reset() {
        writeLock.lock()
        try {
            tokens.set(Tokens())
        } finally {
            writeLock.unlock()
        }
    }

    // Internal Exchange Methods

    private suspend fun exchangeAuthorizationCode(authorizationCode: String): IdTokenExchangeResponse {
        val formData = mapOf(
            "grant_type" to "authorization_code",
            "client_id" to CCloudOAuthConfig.CCLOUD_OAUTH_CLIENT_ID,
            "code" to authorizationCode,
            "code_verifier" to codeVerifier,
            "redirect_uri" to CCloudOAuthConfig.CCLOUD_OAUTH_REDIRECT_URI
        )

        // TODO: HTTP POST to CCLOUD_OAUTH_TOKEN_URI
        // Content-Type: application/x-www-form-urlencoded
        // Body: form data above
        // Response: IdTokenExchangeResponse with id_token, refresh_token, expires_in
        TODO("Implement HTTP call to exchange authorization code")
    }

    private suspend fun createTokensFromRefreshToken(): IdTokenExchangeResponse {
        val refreshToken = tokens.get().refreshToken
        if (isTokenMissing(refreshToken)) {
            throw IllegalStateException("Refresh token is missing.")
        }

        val formData = mapOf(
            "grant_type" to "refresh_token",
            "client_id" to CCloudOAuthConfig.CCLOUD_OAUTH_CLIENT_ID,
            "refresh_token" to refreshToken!!.token,
            "redirect_uri" to CCloudOAuthConfig.CCLOUD_OAUTH_REDIRECT_URI
        )

        // TODO: HTTP POST to CCLOUD_OAUTH_TOKEN_URI
        // Content-Type: application/x-www-form-urlencoded
        // Body: form data above
        // Response: IdTokenExchangeResponse with new id_token, refresh_token, expires_in
        TODO("Implement HTTP call to refresh tokens")
    }


    private suspend fun processTokenExchangeResponse(
        idTokenResponse: IdTokenExchangeResponse,
        organizationId: String?
    ) {
        // Part 1 - Validate and store refresh token
        if (idTokenResponse.error != null) {
            throw IllegalStateException(
                "Retrieving ID token failed: ${idTokenResponse.error} - ${idTokenResponse.errorDescription}"
            )
        }

        val now = Instant.now()

        tokens.updateAndGet { oldTokens ->
            oldTokens.withRefreshToken(
                Token(
                    idTokenResponse.refreshToken ?: throw IllegalStateException("No refresh token"),
                    now.plusSeconds(idTokenResponse.expiresIn ?: 86400)
                )
            )
        }

        // Part 2-  Exchange ID token for control plane token
        val cpResponse = exchangeControlPlaneToken(
            idTokenResponse.idToken ?: throw IllegalStateException("No ID token"),
            organizationId
        )

        if (cpResponse.error != null && cpResponse.error !is JsonNull) {
            throw IllegalStateException("Retrieving control plane token failed: ${cpResponse.error}")
        }

        tokens.updateAndGet { oldTokens ->
            oldTokens
                .withControlPlaneToken(
                    Token(
                        cpResponse.token ?: throw IllegalStateException("No control plane token"),
                        now.plusSeconds(CCloudOAuthConfig.CCLOUD_CONTROL_PLANE_TOKEN_LIFETIME.inWholeSeconds)
                    )
                )
                .let { if (cpResponse.user != null) it.withUser(cpResponse.user) else it }
                .let { if (cpResponse.organization != null) it.withOrganization(cpResponse.organization) else it }
        }

        // Part 3 - Exchange control plane token for data plane token
        val dpResponse = exchangeDataPlaneToken(cpResponse.token!!)

        if (dpResponse.error != null && cpResponse.error !is JsonNull) {
            throw IllegalStateException("Retrieving data plane token failed: ${dpResponse.error}")
        }

        tokens.updateAndGet { oldTokens ->
            oldTokens.withDataPlaneToken(
                Token(
                    dpResponse.token ?: throw IllegalStateException("No data plane token"),
                    now.plusSeconds(CCloudOAuthConfig.CCLOUD_CONTROL_PLANE_TOKEN_LIFETIME.inWholeSeconds)
                )
            )
        }
    }

    // TODO: Create and switch params to ExchangeControlPlaneTokenRequest?
    private suspend fun exchangeControlPlaneToken(
        idToken: String,
        organizationId: String?
    ): ControlPlaneTokenExchangeResponse {
        // TODO: JSON body schema?

        // TODO: HTTP POST to CCLOUD_CONTROL_PLANE_TOKEN_EXCHANGE_URI
        // Content-Type: application/json
        // Body: jsonBody above
        //
        // Extract token from Set-Cookie header
        // Response headers contain: Set-Cookie: auth_token=<CONTROL_PLANE_TOKEN>; ...
        // Parse the cookie and extract "auth_token" value
        //
        // Response body: ControlPlaneTokenExchangeResponse with user, organization (token from cookie)
        TODO("Implement HTTP call to exchange control plane token - extract token from Set-Cookie header")
    }

    private suspend fun exchangeDataPlaneToken(
        controlPlaneToken: String
    ): DataPlaneTokenExchangeResponse {
        // TODO: HTTP POST to CCLOUD_DATA_PLANE_TOKEN_EXCHANGE_URI
        // Content-Type: application/json
        // Authorization: Bearer {controlPlaneToken}
        // Body: {}
        // Response: DataPlaneTokenExchangeResponse with token
        TODO("Implement HTTP call to exchange data plane token")
    }

    /** Helpers */

    private fun isTokenMissing(token: Token?): Boolean =
        token == null || token.token.isEmpty()

    private fun encode(s: String) = URLEncoder.encode(s, Charsets.UTF_8)
}
