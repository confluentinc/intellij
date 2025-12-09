package io.confluent.intellijplugin.ccloud.auth

import kotlin.concurrent.Volatile
import kotlin.concurrent.withLock
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.concurrent.locks.ReentrantLock

/**
 * Manages OAuth authentication state for a Confluent Cloud connection
 */
class CCloudOAuthContext {

    val codeVerifier: String = randomBase64(32)
    val codeChallenge: String = sha256Base64(codeVerifier)
    val oauthState: String = randomBase64(32)

    // Using @Volatile to ensure visibility across threads (atomic operations) kotlin specific
    @Volatile var refreshToken: CCloudTokens.Token? = null; private set
    @Volatile var controlPlaneToken: CCloudTokens.Token? = null; private set
    @Volatile var dataPlaneToken: CCloudTokens.Token? = null; private set

    @Volatile var endOfLifetime: Instant? = null; private set  // End of lifetime (absolute refresh token expiry)

    @Volatile var lastError: String? = null; private set  // Error tracking (simple: just last error + retry count, expand later if needed)
    @Volatile var failedRefreshAttempts: Int = 0; private set

    // Lock for write operations (like sidecar's writeLock)
    private val writeLock = ReentrantLock()

    // Earliest token expiry, used to schedule refresh
    fun expiresAt(): Instant? = listOfNotNull(
        refreshToken?.expiresAt,
        controlPlaneToken?.expiresAt,
        dataPlaneToken?.expiresAt
    ).minOrNull()

    fun hasReachedEndOfLifetime(): Boolean =
        endOfLifetime?.let { Instant.now() >= it } ?: false

    // Check if we should attempt token refresh
    fun shouldAttemptTokenRefresh(): Boolean {
        if (hasReachedEndOfLifetime()) return false
        if (failedRefreshAttempts >= CCloudOAuthConfig.MAX_TOKEN_REFRESH_ATTEMPTS) return false

        val expiresAt = expiresAt() ?: return false
        val nextCheck = Instant.now().plusSeconds(CCloudOAuthConfig.TOKEN_REFRESH_INTERVAL.inWholeSeconds)
        return expiresAt < nextCheck
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

    /** Token Exchange (skeleton, TODO: implement with HTTP client) */
    suspend fun createTokensFromAuthorizationCode(authCode: String, orgId: String?): Result<Unit> = writeLock.withLock {
        endOfLifetime = Instant.now().plusSeconds(
            CCloudOAuthConfig.CCLOUD_REFRESH_TOKEN_ABSOLUTE_LIFETIME.inWholeSeconds
        )
        // TODO: POST CCLOUD_OAUTH_TOKEN_URI grant_type=authorization_code
        // Exchange auth code for tokens (called after OAuth callback)
        Result.failure(Exception("TODO"))
    }

    suspend fun refresh(orgId: String?): Result<Unit> = writeLock.withLock {
        refreshToken ?: return@withLock Result.failure(IllegalStateException("No refresh token"))
        // TODO: POST CCLOUD_OAUTH_TOKEN_URI grant_type=refresh_token
        // Refresh all tokens using refresh_token
        Result.failure(Exception("TODO"))
    }

    suspend fun checkAuthenticationStatus(): Result<Boolean> {
        controlPlaneToken ?: return Result.failure(IllegalStateException("No control plane token"))
        // TODO: GET CCLOUD_CONTROL_PLANE_CHECK_JWT_URI
        return Result.failure(Exception("TODO"))
    }

    fun setRefreshToken(token: String, expiresAt: Instant) = writeLock.withLock {
        refreshToken = CCloudTokens.Token(token, expiresAt)
    }

    fun setControlPlaneToken(token: String) = writeLock.withLock {
        controlPlaneToken = CCloudTokens.Token(
            token,
            Instant.now().plusSeconds(CCloudOAuthConfig.CCLOUD_CONTROL_PLANE_TOKEN_LIFETIME.inWholeSeconds)
        )
    }

    fun setDataPlaneToken(token: String) = writeLock.withLock {
        dataPlaneToken = CCloudTokens.Token(
            token,
            Instant.now().plusSeconds(CCloudOAuthConfig.CCLOUD_CONTROL_PLANE_TOKEN_LIFETIME.inWholeSeconds)
        )
    }

    fun onSuccessfulRefresh() = writeLock.withLock {
        lastError = null
        failedRefreshAttempts = 0
    }

    fun onFailedRefresh(error: String) = writeLock.withLock {
        lastError = error
        failedRefreshAttempts++
    }

    fun reset() = writeLock.withLock {
        refreshToken = null
        controlPlaneToken = null
        dataPlaneToken = null
        endOfLifetime = null
        lastError = null
        failedRefreshAttempts = 0
    }

    /** Helpers */
    private fun encode(s: String) = URLEncoder.encode(s, Charsets.UTF_8)

    companion object {
        private val secureRandom = SecureRandom()
        private val base64Encoder = Base64.getUrlEncoder().withoutPadding()

        private fun randomBase64(bytes: Int): String =
            ByteArray(bytes).also { secureRandom.nextBytes(it) }.let { base64Encoder.encodeToString(it) }

        private fun sha256Base64(input: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(input.toByteArray(Charsets.US_ASCII))
                .let { base64Encoder.encodeToString(it) }
    }
}
