package io.confluent.intellijplugin.ccloud.auth

import com.intellij.util.io.HttpRequests
import io.confluent.intellijplugin.ccloud.auth.CCloudOAuthHttpClient.HttpResponseWithHeaders
import io.confluent.intellijplugin.ccloud.client.CCloudRetryPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonNull
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference

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

    /** Immutable snapshot; readers take one `get()` and need no lock. */
    private val tokens = AtomicReference(Tokens())

    /**
     * Serializes the suspend operations that talk to the token endpoints (sign-in, refresh,
     * auth-status check), so a refresh token is never used by two exchanges at once. A coroutine
     * [Mutex] rather than a thread-owned lock because these calls suspend into `Dispatchers.IO` and
     * may resume on another thread. Not reentrant: a guarded method must not call another one.
     * [withLock] releases it if the caller is cancelled (e.g. a sign-in that times out).
     */
    private val exchangeMutex = Mutex()

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
    fun expiresAt(): Instant? = tokens.get().expiresAt()

    fun hasReachedEndOfLifetime(): Boolean {
        val endOfLifetime = getEndOfLifetime() ?: return false
        return Instant.now() >= endOfLifetime
    }

    fun hasNonTransientError(): Boolean = tokens.get().errors.hasNonTransientErrors()

    fun shouldAttemptTokenRefresh(): Boolean {
        val snapshot = tokens.get()
        val now = Instant.now()
        val expiresAt = snapshot.expiresAt() ?: return false
        val nextExecution = now.plusSeconds(CCloudOAuthConfig.CHECK_TOKEN_EXPIRATION_INTERVAL.inWholeSeconds)
        val atLeastOneTokenWillExpireBeforeNextRun = expiresAt < nextExecution
        val reachedEndOfLifetime = snapshot.endOfLifetime?.let { now >= it } ?: false

        return !reachedEndOfLifetime
                && !snapshot.errors.hasNonTransientErrors()
                && atLeastOneTokenWillExpireBeforeNextRun
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
    ): Result<CCloudOAuthContext> = exchangeMutex.withLock {
        runCatchingNonCancellation {
            // Set absolute lifetime on fresh sign-in
            tokens.updateAndGet { oldTokens ->
                oldTokens.withEndOfLifetime(
                    Instant.now()
                        .plusSeconds(CCloudOAuthConfig.CCLOUD_REFRESH_TOKEN_ABSOLUTE_LIFETIME.inWholeSeconds)
                )
            }

            processTokenExchangeResponse(exchangeAuthorizationCode(authorizationCode), organizationId)
        }.onSuccess {
            // Reset any existing errors and the refresh failure count (and so the backoff)
            tokens.updateAndGet { oldTokens ->
                oldTokens.withSuccessfulTokenRefreshAttempt().let {
                    it.withErrors(it.errors.withoutSignIn().withoutAuthStatusCheck())
                }
            }
        }.onFailure { failure ->
            tokens.updateAndGet { oldTokens ->
                oldTokens.withErrors(oldTokens.errors.withSignIn(failure.message ?: "Unknown error"))
            }
        }.map { this }
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
    suspend fun refresh(organizationId: String? = null): Result<CCloudOAuthContext> = exchangeMutex.withLock {
        runCatchingNonCancellation {
            processTokenExchangeResponse(createTokensFromRefreshToken(), organizationId)
        }.onSuccess {
            // Reset failed attempts counter on success
            tokens.updateAndGet { it.withSuccessfulTokenRefreshAttempt() }
        }.onFailure { failure ->
            // Track failed attempt - becomes non-transient after MAX_TOKEN_REFRESH_ATTEMPTS
            tokens.updateAndGet {
                it.withFailedTokenRefreshAttempt(
                    failure,
                    CCloudOAuthConfig.MAX_TOKEN_REFRESH_ATTEMPTS
                )
            }
        }.map { this }
    }

    /**
     * Refresh tokens without tracking failures.
     * @param organizationId The Confluent Cloud organization ID
     * @return If successful, returns a CCloudOAuthContext with up-to-date refresh, control plane,
     * and data plane tokens. If not successful, returns a failed Result holding the cause of the failure.
     */
    suspend fun refreshIgnoreFailures(organizationId: String? = null): Result<CCloudOAuthContext> = exchangeMutex.withLock {
        // Note: failures are NOT tracked
        runCatchingNonCancellation {
            processTokenExchangeResponse(createTokensFromRefreshToken(), organizationId)
        }.onSuccess {
            tokens.updateAndGet { it.withSuccessfulTokenRefreshAttempt() }
        }.map { this }
    }

    /**
     * Verify control plane token is valid by performing an API call against Confluent Cloud's api/check_jwt endpoint.
     * @return If successful, returns a Result holding the boolean value true if the token is valid.
     * If not successful, returns a failed Result holding the cause of the failure.
     */
    suspend fun checkAuthenticationStatus(): Result<Boolean> = exchangeMutex.withLock {
        run {
            val controlPlaneToken = tokens.get().controlPlaneToken
            if (isTokenMissing(controlPlaneToken)) {
                val errorMessage = "Cannot verify authentication status: no control plane token available."
                tokens.updateAndGet { it.withErrors(it.errors.withAuthStatusCheck(errorMessage)) }
                return@run Result.failure(IllegalStateException(errorMessage))
            }

            runCatchingNonCancellation {
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
        }
    }

    /**
     * Clear all tokens and errors. Not guarded by [exchangeMutex] (it is not a suspend function);
     * a refresh still in flight could write tokens back afterwards. Only tests call it today:
     * sign-out drops the whole context instead.
     */
    fun reset() {
        tokens.set(Tokens())
    }

    // Internal Exchange Methods

    /**
     * Exchanges the authorization code for an ID token.
     * @param authorizationCode The authorization code received from Confluent Cloud
     * @return The raw `oauth/token` response
     */
    private suspend fun exchangeAuthorizationCode(authorizationCode: String): HttpResponseWithHeaders {
        val formData = mapOf(
            "grant_type" to "authorization_code",
            "client_id" to CCloudOAuthConfig.CCLOUD_OAUTH_CLIENT_ID,
            "code" to authorizationCode,
            "code_verifier" to codeVerifier,
            "redirect_uri" to CCloudOAuthConfig.CCLOUD_OAUTH_REDIRECT_URI
        )

        return withServerErrorsTyped(TokenEndpoint.OAUTH_TOKEN) {
            CCloudOAuthHttpClient.postFormWithHeaders(
                url = CCloudOAuthConfig.CCLOUD_OAUTH_TOKEN_URI,
                formData = formData
            )
        }
    }

    private suspend fun createTokensFromRefreshToken(): HttpResponseWithHeaders {
        val refreshToken = tokens.get().refreshToken
        if (isTokenMissing(refreshToken)) {
            throw MissingRefreshTokenException()
        }

        val formData = mapOf(
            "grant_type" to "refresh_token",
            "client_id" to CCloudOAuthConfig.CCLOUD_OAUTH_CLIENT_ID,
            "refresh_token" to refreshToken!!.token,
            "redirect_uri" to CCloudOAuthConfig.CCLOUD_OAUTH_REDIRECT_URI
        )

        return withServerErrorsTyped(TokenEndpoint.OAUTH_TOKEN) {
            CCloudOAuthHttpClient.postFormWithHeaders(
                url = CCloudOAuthConfig.CCLOUD_OAUTH_TOKEN_URI,
                formData = formData
            )
        }
    }


    /**
     * Processes the response of Confluent Cloud's oauth/token endpoint, which includes the ID
     * token, exchanges the ID token for a control plane token, and exchanges the control plane for a data plane token.
     * @param oauthTokenResponse The raw response of the oauth/token endpoint holding the ID token
     * @param organizationId The Confluent Cloud organization ID
     * @return If successful, updates the CCloudOAuthContext with up-to-date refresh, control plane,
     * and data plane tokens. If not successful, throws at the first point of failure; endpoint errors
     * are a [TokenEndpointException] (an [OAuthErrorException] for oauth/token errors).
     */
    private suspend fun processTokenExchangeResponse(
        oauthTokenResponse: HttpResponseWithHeaders,
        organizationId: String?
    ) {
        // Part 1 - Validate and store refresh token
        val idTokenResponse = parseIdTokenResponse(oauthTokenResponse)

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

        val response = withServerErrorsTyped(TokenEndpoint.CONTROL_PLANE) {
            CCloudOAuthHttpClient.postJsonWithHeaders(
                url = CCloudOAuthConfig.CCLOUD_CONTROL_PLANE_TOKEN_EXCHANGE_URI,
                jsonBody = jsonBody
            )
        }

        val cpResponse = decodeTokenEndpointResponse<ControlPlaneTokenExchangeResponse>(
            TokenEndpoint.CONTROL_PLANE, response, "Retrieving control plane token failed"
        )
        if (cpResponse.error != null && cpResponse.error !is JsonNull) {
            throw TokenEndpointException(
                endpoint = TokenEndpoint.CONTROL_PLANE,
                httpStatusCode = response.statusCode,
                retryAfterHeader = response.header(CCloudRetryPolicy.RETRY_AFTER_HEADER),
                message = "Retrieving control plane token failed: ${cpResponse.error}"
            )
        }

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
        val response = withServerErrorsTyped(TokenEndpoint.DATA_PLANE) {
            CCloudOAuthHttpClient.postJsonWithHeaders(
                url = CCloudOAuthConfig.CCLOUD_DATA_PLANE_TOKEN_EXCHANGE_URI,
                jsonBody = "{}",
                bearerToken = controlPlaneToken
            )
        }

        val dpResponse = decodeTokenEndpointResponse<DataPlaneTokenExchangeResponse>(
            TokenEndpoint.DATA_PLANE, response, "Retrieving data plane token failed"
        )
        if (dpResponse.error != null && dpResponse.error !is JsonNull) {
            throw TokenEndpointException(
                endpoint = TokenEndpoint.DATA_PLANE,
                httpStatusCode = response.statusCode,
                retryAfterHeader = response.header(CCloudRetryPolicy.RETRY_AFTER_HEADER),
                message = "Retrieving data plane token failed: ${dpResponse.error}"
            )
        }
        return dpResponse
    }

    /**
     * [CCloudOAuthHttpClient.readResponseBody] throws [HttpRequests.HttpStatusException] for 5xx
     * before the caller sees the response. Re-throw it as a [TokenEndpointException] so every
     * endpoint failure carries its step and status (the message is unchanged).
     */
    private inline fun <T> withServerErrorsTyped(endpoint: TokenEndpoint, block: () -> T): T =
        try {
            block()
        } catch (e: HttpRequests.HttpStatusException) {
            throw TokenEndpointException(
                endpoint = endpoint,
                httpStatusCode = e.statusCode,
                message = e.message ?: "Server error",
                cause = e
            )
        }

    /**
     * Parse the oauth/token response. An OAuth error body becomes an [OAuthErrorException]; any
     * other non-2xx response becomes a [TokenEndpointException]. Both keep the HTTP status.
     */
    private fun parseIdTokenResponse(response: HttpResponseWithHeaders): IdTokenExchangeResponse {
        val retryAfter = response.header(CCloudRetryPolicy.RETRY_AFTER_HEADER)
        val parsed = runCatching {
            CCloudOAuthHttpClient.json.decodeFromString<IdTokenExchangeResponse>(response.body)
        }

        parsed.getOrNull()?.error?.let { errorCode ->
            throw OAuthErrorException(
                errorCode = errorCode,
                errorDescription = parsed.getOrNull()?.errorDescription?.toString(),
                httpStatusCode = response.statusCode,
                retryAfterHeader = retryAfter
            )
        }
        if (!response.isSuccessful) {
            throw TokenEndpointException(
                endpoint = TokenEndpoint.OAUTH_TOKEN,
                httpStatusCode = response.statusCode,
                retryAfterHeader = retryAfter,
                message = "Retrieving ID token failed: HTTP ${response.statusCode}"
            )
        }
        return parsed.getOrThrow()
    }

    /**
     * Decode a CCloud token endpoint body, throwing a [TokenEndpointException] that carries the
     * status if the response is not 2xx (whether or not the body parses).
     */
    private inline fun <reified T> decodeTokenEndpointResponse(
        endpoint: TokenEndpoint,
        response: HttpResponseWithHeaders,
        failurePrefix: String
    ): T {
        if (!response.isSuccessful) {
            throw TokenEndpointException(
                endpoint = endpoint,
                httpStatusCode = response.statusCode,
                retryAfterHeader = response.header(CCloudRetryPolicy.RETRY_AFTER_HEADER),
                // Body left out on purpose: the message reaches logs and telemetry
                message = "$failurePrefix: HTTP ${response.statusCode}"
            )
        }
        return CCloudOAuthHttpClient.json.decodeFromString<T>(response.body)
    }

    // Helpers

    /**
     * [runCatching] that lets [CancellationException] through, so a cancelled sign-in or refresh
     * (e.g. the refresh loop stopped, or a sign-in that timed out) propagates as cancellation
     * instead of being recorded as a failed attempt or a sign-in error.
     */
    private inline fun <T> runCatchingNonCancellation(block: () -> T): Result<T> =
        runCatching(block).onFailure { if (it is CancellationException) throw it }

    private fun isTokenMissing(token: Token?): Boolean =
        token == null || token.token.isEmpty()

    private fun encode(s: String) = URLEncoder.encode(s, Charsets.UTF_8)
}
