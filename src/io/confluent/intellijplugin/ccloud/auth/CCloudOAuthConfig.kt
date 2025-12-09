package io.confluent.intellijplugin.ccloud.auth

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration for Confluent Cloud OAuth 2.0
 * @see CCloudOAuthContext
 */
object CCloudOAuthConfig {

    private enum class CCloudEnv {
        PROD, STAG, DEVEL;

        companion object {
            fun of(basePath: String?): CCloudEnv = when (basePath) {
                "stag.cpdev.cloud" -> STAG
                "devel.cpdev.cloud" -> DEVEL
                else -> PROD
            }
        }
    }

    // Resolved environment based on system property or environment variable. -> Could improve to experience in IDE
    private val env: CCloudEnv = run {
        val basePath = System.getProperty("ccloud.base-path")
            ?: System.getenv("CCLOUD_BASE_PATH")
        CCloudEnv.of(basePath)
    }

    // OAuth Configurations
    val CCLOUD_OAUTH_CLIENT_ID: String = when (env) {
        CCloudEnv.PROD -> "wlBAWfRbGnxAPwROp25kpNwiYGEdsici"
        CCloudEnv.STAG -> "6TSqajfJykLdBbSPhvsWc3sCuITvrnL7"
        CCloudEnv.DEVEL -> "m94Mb54lGbyX9XVkrl5Zj9YyrFVc2XTi"
    }

    val CCLOUD_OAUTH_AUTHORIZE_URI: String = when (env) {
        CCloudEnv.PROD -> "https://login.confluent.io/oauth/authorize"
        CCloudEnv.STAG -> "https://login-stag.confluent-dev.io/oauth/authorize"
        CCloudEnv.DEVEL -> "https://login.confluent-dev.io/oauth/authorize"
    }

    val CCLOUD_OAUTH_TOKEN_URI: String = when (env) {
        CCloudEnv.PROD -> "https://login.confluent.io/oauth/token"
        CCloudEnv.STAG -> "https://login-stag.confluent-dev.io/oauth/token"
        CCloudEnv.DEVEL -> "https://login.confluent-dev.io/oauth/token"
    }

    const val CCLOUD_OAUTH_SCOPE: String = "email openid offline_access"

    // Control Plane Configurations
    val CCLOUD_CONTROL_PLANE_TOKEN_LIFETIME: Duration = 5.seconds

    val CCLOUD_CONTROL_PLANE_TOKEN_EXCHANGE_URI: String = when (env) {
        CCloudEnv.PROD -> "https://confluent.cloud/api/sessions"
        CCloudEnv.STAG -> "https://stag.cpdev.cloud/api/sessions"
        CCloudEnv.DEVEL -> "https://devel.cpdev.cloud/api/sessions"
    }

    val CCLOUD_CONTROL_PLANE_CHECK_JWT_URI: String = when (env) {
        CCloudEnv.PROD -> "https://confluent.cloud/api/check_jwt"
        CCloudEnv.STAG -> "https://stag.cpdev.cloud/api/check_jwt"
        CCloudEnv.DEVEL -> "https://devel.cpdev.cloud/api/check_jwt"
    }

    // Data Plane Configurations
    val CCLOUD_DATA_PLANE_TOKEN_EXCHANGE_URI: String = when (env) {
        CCloudEnv.PROD -> "https://confluent.cloud/api/access_tokens"
        CCloudEnv.STAG -> "https://stag.cpdev.cloud/api/access_tokens"
        CCloudEnv.DEVEL -> "https://devel.cpdev.cloud/api/access_tokens"
    }

    // Other
    val CCLOUD_REFRESH_TOKEN_ABSOLUTE_LIFETIME: Duration = 8.hours
    val TOKEN_REFRESH_INTERVAL: Duration = 5.seconds
    const val MAX_TOKEN_REFRESH_ATTEMPTS = 50
    const val CALLBACK_PORT: Int = 26638
    const val CALLBACK_PATH: String = "/gateway/v1/callback-intellij-docs"
    const val CCLOUD_OAUTH_REDIRECT_URI: String = "http://127.0.0.1:${CALLBACK_PORT}${CALLBACK_PATH}"
}
