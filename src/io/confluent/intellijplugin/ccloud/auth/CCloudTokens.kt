package io.confluent.intellijplugin.ccloud.auth

import java.time.Instant
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

/**
 * Token and state types for Confluent Cloud OAuth
 * @property expiresAt when the server stops accepting the token
 * @property refreshAt when to start replacing it; defaults to [expiresAt]
 * @see CCloudOAuthContext
 */
data class Token(val token: String, val expiresAt: Instant, val refreshAt: Instant = expiresAt) {
    companion object {
        /**
         * A token issued at [issuedAt] that lives for [lifetime]. Its refresh point is a random point in
         * [CCloudOAuthConfig.TOKEN_REFRESH_WINDOW] of the lifetime, drawn once: early enough that a slow
         * or failed refresh can be retried before the token expires, and spread out so signed-in IDEs
         * don't all refresh at the same moment.
         */
        fun issued(token: String, issuedAt: Instant, lifetime: Duration, random: Random = Random.Default): Token {
            val window = CCloudOAuthConfig.TOKEN_REFRESH_WINDOW
            val fraction = random.nextDouble(window.start, window.endExclusive)
            return Token(
                token = token,
                expiresAt = issuedAt.plus(lifetime.toJavaDuration()),
                refreshAt = issuedAt.plus((lifetime * fraction).toJavaDuration()),
            )
        }
    }
}

@Serializable
@JsonIgnoreUnknownKeys
data class UserDetails(
    val id: String,
    val email: String,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("resource_id") val resourceId: String? = null,
    @SerialName("service_account") val serviceAccount: Boolean? = null,
    @SerialName("social_connection") val socialConnection: String? = null,
    @SerialName("auth_type") val authType: String? = null,
)

@Serializable
@JsonIgnoreUnknownKeys
data class OrganizationDetails(
    val id: String,
    val name: String,
    @SerialName("resource_id") val resourceId: String? = null,
    val sso: SsoDetails,
)

@Serializable
@JsonIgnoreUnknownKeys
data class SsoDetails(
    val enabled: Boolean,
    @SerialName("auth0_connection_name") val auth0ConnectionName: String? = null,
    @SerialName("tenant_id") val tenantId: String? = null,
    @SerialName("multi_tenant") val multiTenant: Boolean? = null,
    val mode: String,
    @SerialName("connection_name") val connectionName: String? = null,
    val vendor: String,
    @SerialName("jit_enabled") val jitEnabled: Boolean? = null,
    @SerialName("bup_enabled") val bupEnabled: Boolean? = null,
)

/**
 * Immutable token state container
 * @see CCloudOAuthContext
 */
data class Tokens(
    val refreshToken: Token? = null,
    val controlPlaneToken: Token? = null,
    val dataPlaneToken: Token? = null,
    val user: UserDetails? = null,
    val organization: OrganizationDetails? = null,
    val endOfLifetime: Instant? = null,
    val errors: AuthErrors = AuthErrors(),
    val failedTokenRefreshAttempts: Int = 0
) {
    fun expiresAt(): Instant? = listOfNotNull(
        refreshToken?.expiresAt,
        controlPlaneToken?.expiresAt,
        dataPlaneToken?.expiresAt
    ).minOrNull()

    /** Whether the earliest [Token.refreshAt] of the tokens falls before [deadline]. */
    fun isRefreshDueBefore(deadline: Instant): Boolean =
        listOfNotNull(refreshToken?.refreshAt, controlPlaneToken?.refreshAt, dataPlaneToken?.refreshAt)
            .minOrNull()
            ?.let { it < deadline }
            ?: false

    fun hasReachedEndOfLifetime(now: Instant = Instant.now()): Boolean =
        endOfLifetime?.let { now >= it } ?: false

    // Immutable update methods
    fun withRefreshToken(token: Token) = copy(refreshToken = token)
    fun withControlPlaneToken(token: Token) = copy(controlPlaneToken = token)
    fun withDataPlaneToken(token: Token) = copy(dataPlaneToken = token)
    fun withUser(user: UserDetails) = copy(user = user)
    fun withOrganization(org: OrganizationDetails) = copy(organization = org)
    fun withEndOfLifetime(instant: Instant) = copy(endOfLifetime = instant)
    fun withErrors(errors: AuthErrors) = copy(errors = errors)

    fun withSuccessfulTokenRefreshAttempt() = copy(
        errors = errors.withoutTokenRefresh(),
        failedTokenRefreshAttempts = 0
    )

    /**
     * Record a failed refresh. The failure is non-transient (stop refreshing, sign out) when the
     * error is terminal or when this was attempt number [maxAttempts], so at most [maxAttempts]
     * refreshes are tried in a row.
     */
    fun withFailedTokenRefreshAttempt(error: Throwable, maxAttempts: Int): Tokens {
        val attempts = failedTokenRefreshAttempts + 1
        val isTransient = attempts < maxAttempts && !TokenRefreshErrorClassifier.isTerminal(error)
        return copy(
            errors = errors.withTokenRefresh(error.message ?: "Unknown error", isTransient),
            failedTokenRefreshAttempts = attempts
        )
    }
}
