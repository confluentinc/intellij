package io.confluent.intellijplugin.ccloud.auth

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

    val CCLOUD_OAUTH_AUTHORIZE_URI: String = "https://$loginBasePath/oauth/authorize"
    val CCLOUD_OAUTH_TOKEN_URI: String = "https://$loginBasePath/oauth/token"

    const val CCLOUD_OAUTH_SCOPE: String = "email openid offline_access"

    // Control Plane Configurations
    val CCLOUD_CONTROL_PLANE_TOKEN_LIFETIME: Duration = 5.minutes
    val CCLOUD_CONTROL_PLANE_TOKEN_EXCHANGE_URI: String = "https://$basePath/api/sessions"
    val CCLOUD_CONTROL_PLANE_CHECK_JWT_URI: String = "https://$basePath/api/check_jwt"

    // Data Plane Configurations
    val CCLOUD_DATA_PLANE_TOKEN_EXCHANGE_URI: String = "https://$basePath/api/access_tokens"

    // Other
    val CCLOUD_REFRESH_TOKEN_ABSOLUTE_LIFETIME: Duration = 8.hours
    val CHECK_TOKEN_EXPIRATION_INTERVAL: Duration = 5.seconds
    const val MAX_TOKEN_REFRESH_ATTEMPTS = 50

    val CALLBACK_PORT: Int = System.getProperty("ccloud.callback-port")?.toIntOrNull() ?: 26638
    const val CALLBACK_PATH: String = "/gateway/v1/callback-intellij-docs"
    val CCLOUD_OAUTH_REDIRECT_URI: String = "http://127.0.0.1:${CALLBACK_PORT}${CALLBACK_PATH}"
}
