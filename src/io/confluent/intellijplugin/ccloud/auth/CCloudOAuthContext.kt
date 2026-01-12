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

    /**
     * Earliest token expiry, used to schedule refresh
     * @return The earliest token expiry time, or null if no tokens are present.
     */
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

    /**
     * Creates a fully authenticated CCloudOAuthContext using the authorization code passed from the redirect_uri callback.
     * @param authorizationCode The authorization code received from Confluent Cloud
     * @param organizationId The Confluent Cloud organization ID
     * @return If successful, returns a CCloudOAuthContext with up-to-date refresh, control plane,
     * and data plane tokens. If not successful, returns a failed Result holding the cause of the failure.
     */
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

                val (statusCode, idTokenResponse) = exchangeAuthorizationCode(authorizationCode)
                processTokenExchangeResponse(statusCode, idTokenResponse, organizationId)
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
     * Refreshes all tokens using refresh_token (refresh token, control plane
     * token, and data plane token). Starts by exchanging the refresh token for an ID token, which then gets
     * exchanged for the control plane token, and finally for the data plane token. The refresh token implicitly gets
     * invalidated when exchanging for the ID token, and a new refresh token is returned.
     * @param organizationId The Confluent Cloud organization ID
     * @return If successful, returns a CCloudOAuthContext with up-to-date refresh, control plane,
     * and data plane tokens. If not successful, returns a failed Result holding the cause of the failure.
     */
    suspend fun refresh(organizationId: String? = null): Result<CCloudOAuthContext> {
        writeLock.lock()
        try {
            return runCatching {
                val (statusCode, idTokenResponse) = createTokensFromRefreshToken()
                processTokenExchangeResponse(statusCode, idTokenResponse, organizationId)
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

    /**
     * Refresh tokens without tracking failures.
     * @param organizationId The Confluent Cloud organization ID
     * @return If successful, returns a CCloudOAuthContext with up-to-date refresh, control plane,
     * and data plane tokens. If not successful, returns a failed Result holding the cause of the failure.
     */
    suspend fun refreshIgnoreFailures(organizationId: String? = null): Result<CCloudOAuthContext> {
        writeLock.lock()
        try {
            return runCatching {
                val (statusCode, idTokenResponse) = createTokensFromRefreshToken()
                processTokenExchangeResponse(statusCode, idTokenResponse, organizationId)
            }.onSuccess {
                tokens.updateAndGet { it.withSuccessfulTokenRefreshAttempt() }
            }.map { this }
            // Note: failures are NOT tracked
        } finally {
            writeLock.unlock()
        }
    }

    /**
     * Verify control plane token is valid by performing an API call against Confluent Cloud's api/check_jwt endpoint.
     * @return If successful, returns a Result holding the boolean value true if the token is valid.
     * If not successful, returns a failed Result holding the cause of the failure.
     */
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
                val response = CCloudOAuthHttpClient.get<CheckJwtResponse>(
                    url = CCloudOAuthConfig.CCLOUD_CONTROL_PLANE_CHECK_JWT_URI,
                    bearerToken = controlPlaneToken!!.token
                )

                if (response.error != null && response.error !is JsonNull) {
                    throw IllegalStateException("JWT validation failed: ${response.error}")
                }
                true
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

    /**
     * Exchanges the authorization code for an ID token.
     * @param authorizationCode The authorization code received from Confluent Cloud
     * @return Pair of (HTTP status code, ID token exchange response)
     */
    private suspend fun exchangeAuthorizationCode(authorizationCode: String): Pair<Int, IdTokenExchangeResponse> {
        val formData = mapOf(
            "grant_type" to "authorization_code",
            "client_id" to CCloudOAuthConfig.CCLOUD_OAUTH_CLIENT_ID,
            "code" to authorizationCode,
            "code_verifier" to codeVerifier,
            "redirect_uri" to CCloudOAuthConfig.CCLOUD_OAUTH_REDIRECT_URI
        )

        return CCloudOAuthHttpClient.postFormWithStatus(
            url = CCloudOAuthConfig.CCLOUD_OAUTH_TOKEN_URI,
            formData = formData
        )
    }

    private suspend fun createTokensFromRefreshToken(): Pair<Int, IdTokenExchangeResponse> {
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

        return CCloudOAuthHttpClient.postFormWithStatus(
            url = CCloudOAuthConfig.CCLOUD_OAUTH_TOKEN_URI,
            formData = formData
        )
    }


    /**
     * Processes the response of Confluent Cloud's oauth/token endpoint, which includes the ID
     * token, exchanges the ID token for a control plane token, and exchanges the control plane for a data plane token.
     * @param httpStatusCode The HTTP status code from the token endpoint
     * @param idTokenResponse The response of CCloud's /oauth/token endpoint holding the ID token
     * @param organizationId The Confluent Cloud organization ID
     * @return If successful, updates the CCloudOAuthContext with up-to-date refresh, control plane,
     * and data plane tokens. If not successful, throws an OAuthErrorException at the first point of failure.
     */
    private suspend fun processTokenExchangeResponse(
        httpStatusCode: Int,
        idTokenResponse: IdTokenExchangeResponse,
        organizationId: String?
    ) {
        // Part 1 - Validate and store refresh token
        if (idTokenResponse.error != null) {
            throw OAuthErrorException(
                errorCode = idTokenResponse.error,
                errorDescription = idTokenResponse.errorDescription?.toString(),
                httpStatusCode = httpStatusCode
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

        // Part 2 - Exchange ID token for control plane token
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

        if (dpResponse.error != null && dpResponse.error !is JsonNull) {
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

    /**
     * Exchange ID token for control plane token. The control plane token is returned in the Set-Cookie header, not the response body.
     * @param idToken Confluent Cloud ID token from exchanging the authorization code
     * @param organizationId The Confluent Cloud organization ID
     * @return The control plane token exchange response
     */
    private suspend fun exchangeControlPlaneToken(
        idToken: String,
        organizationId: String?
    ): ControlPlaneTokenExchangeResponse {
        val request = ControlPlaneTokenExchangeRequest(
            idToken = idToken,
            organizationId = organizationId
        )
        val jsonBody = CCloudOAuthHttpClient.json.encodeToString(request)

        val response = CCloudOAuthHttpClient.postJsonWithHeaders(
            url = CCloudOAuthConfig.CCLOUD_CONTROL_PLANE_TOKEN_EXCHANGE_URI,
            jsonBody = jsonBody
        )

        // Parse response body
        val cpResponse = CCloudOAuthHttpClient.json.decodeFromString<ControlPlaneTokenExchangeResponse>(response.body)

        // Extract token from Set-Cookie header
        val authToken = CCloudOAuthHttpClient.extractCookie(response.headers, "auth_token")
            ?: throw IllegalStateException("No auth_token cookie in response")

        return cpResponse.withToken(authToken)
    }

    /**
     * Exchange control plane token for data plane token.
     * @param controlPlaneToken The Confluent Cloud control plane token
     * @return The data plane token exchange response
     */
    private suspend fun exchangeDataPlaneToken(
        controlPlaneToken: String
    ): DataPlaneTokenExchangeResponse {
        return CCloudOAuthHttpClient.postJson(
            url = CCloudOAuthConfig.CCLOUD_DATA_PLANE_TOKEN_EXCHANGE_URI,
            jsonBody = "{}",
            bearerToken = controlPlaneToken
        )
    }

    // Helpers

    private fun isTokenMissing(token: Token?): Boolean =
        token == null || token.token.isEmpty()

    private fun encode(s: String) = URLEncoder.encode(s, Charsets.UTF_8)
}
