package io.confluent.intellijplugin.ccloud.auth

import io.confluent.intellijplugin.ccloud.client.CCloudRetryPolicy
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration for Confluent Cloud OAuth 2.0
 *
 * Environment can be configured via:
 * - System property flag: -Dccloud.env=[prod|stag|devel] (default: prod)
 *
 * Callback port can be configured via:
 * - System property flag: -Dccloud.callback-port=<port> (default: 26638 for production, 26639 for tests)
 *
 * @see CCloudOAuthContext
 */
object CCloudOAuthConfig {

    private enum class CCloudEnv {
        PROD, STAG, DEVEL
    }

    // Resolved environment and base path from system property
    private val envProperty: String = System.getProperty("ccloud.env") ?: "prod"

    private val env: CCloudEnv = when (envProperty) {
        "prod" -> CCloudEnv.PROD
        "stag" -> CCloudEnv.STAG
        "devel" -> CCloudEnv.DEVEL
        else -> error("Unknown environment: $envProperty. Valid values: prod, stag, devel")
    }

    private val basePath: String = when (env) {
        CCloudEnv.PROD -> "confluent.cloud"
        CCloudEnv.STAG -> "stag.cpdev.cloud"
        CCloudEnv.DEVEL -> "devel.cpdev.cloud"
    }

    private val loginBasePath: String = when (env) {
        CCloudEnv.PROD -> "login.confluent.io"
        CCloudEnv.STAG -> "login-stag.confluent-dev.io"
        CCloudEnv.DEVEL -> "login.confluent-dev.io"
    }

    // OAuth Configurations
    val CCLOUD_OAUTH_CLIENT_ID: String = when (env) {
        CCloudEnv.PROD -> "wlBAWfRbGnxAPwROp25kpNwiYGEdsici"
        CCloudEnv.STAG -> "6TSqajfJykLdBbSPhvsWc3sCuITvrnL7"
        CCloudEnv.DEVEL -> "m94Mb54lGbyX9XVkrl5Zj9YyrFVc2XTi"
    }

    // URL properties with test override support via system properties
    val CCLOUD_OAUTH_AUTHORIZE_URI: String
        get() = System.getProperty("ccloud.oauth.authorize-uri") ?: "https://$loginBasePath/oauth/authorize"
    val CCLOUD_OAUTH_TOKEN_URI: String
        get() = System.getProperty("ccloud.oauth.token-uri") ?: "https://$loginBasePath/oauth/token"

    const val CCLOUD_OAUTH_SCOPE: String = "email openid offline_access"

    // Control Plane Configurations
    val CCLOUD_CONTROL_PLANE_TOKEN_LIFETIME: Duration
        get() = System.getProperty("ccloud.control-plane.token-lifetime-seconds")
            ?.toIntOrNull()?.seconds ?: 5.minutes
    val CCLOUD_CONTROL_PLANE_TOKEN_EXCHANGE_URI: String
        get() = System.getProperty("ccloud.control-plane.token-exchange-uri") ?: "https://$basePath/api/sessions"
    val CCLOUD_CONTROL_PLANE_CHECK_JWT_URI: String
        get() = System.getProperty("ccloud.control-plane.check-jwt-uri") ?: "https://$basePath/api/check_jwt"

    // Data Plane Configurations
    val CCLOUD_DATA_PLANE_TOKEN_EXCHANGE_URI: String
        get() = System.getProperty("ccloud.data-plane.token-exchange-uri") ?: "https://$basePath/api/access_tokens"

    // Other
    val CCLOUD_REFRESH_TOKEN_ABSOLUTE_LIFETIME: Duration
        get() = System.getProperty("ccloud.refresh-token.absolute-lifetime-seconds")
            ?.toLongOrNull()?.seconds ?: 8.hours
    val CHECK_TOKEN_EXPIRATION_INTERVAL: Duration = 5.seconds

    /** Consecutive failed refreshes (including the first) before the session is signed out. */
    const val MAX_TOKEN_REFRESH_ATTEMPTS = 10

    /**
     * Wait between failed token refreshes: 5s growing by the policy's default 1.6x, capped at
     * 5 min, plus up to 20% jitter. Before jitter the 9 waits are about 5, 8, 12.8, 20.5, 32.8,
     * 52.4, 83.9, 134.2 and 214.7s, so a transient outage is ridden out for about 9.4 min (up to
     * ~11.3 min with jitter) in 10 requests before sign-out. Computed backoff peaks at ~215s, so
     * the 5 min cap only applies to a 429's Retry-After, which is used as the floor for the next wait.
     */
    val TOKEN_REFRESH_RETRY_POLICY = CCloudRetryPolicy(
        maxAttempts = MAX_TOKEN_REFRESH_ATTEMPTS,
        initialBackoff = 5.seconds,
        maxBackoff = 5.minutes,
        jitter = 0.2,
        maxRetryAfter = 5.minutes,
    )

    val CALLBACK_PORT: Int = System.getProperty("ccloud.callback-port")?.toIntOrNull() ?: 26638
    const val CALLBACK_PATH: String = "/gateway/v1/callback-intellij-docs"
    val CCLOUD_OAUTH_REDIRECT_URI: String = "http://127.0.0.1:${CALLBACK_PORT}${CALLBACK_PATH}"
}
