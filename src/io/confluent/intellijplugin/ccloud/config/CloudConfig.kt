package io.confluent.intellijplugin.ccloud.config

/**
 * Configuration for Confluent Cloud API endpoints and settings.
 */
object CloudConfig {
    /**
     * Base path for Confluent Cloud (production).
     * Staging and dev environments will be added in the future.
     */
    const val BASE_PATH = "confluent.cloud"

    val CONTROL_PLANE_BASE_URL: String
        get() = "https://api.$BASE_PATH"

    object ControlPlane {
        const val ORG_LIST_URI = "/api/org/v2/organizations"
        const val ENV_LIST_URI = "/api/org/v2/environments"
        const val LKC_LIST_URI = "/api/cmk/v2/clusters?environment=%s"
        const val SR_LIST_URI = "/api/srcm/v3/clusters?environment=%s"
    }

    object OAuth {
        object AuthorizeUri {
            const val PROD = "https://login.confluent.io/oauth/authorize"
            const val STAG = "https://login-stag.confluent-dev.io/oauth/authorize"
            const val DEVEL = "https://login.confluent-dev.io/oauth/authorize"
        }

        object IdTokenExchangeUri {
            const val PROD = "https://login.confluent.io/oauth/token"
            const val STAG = "https://login-stag.confluent-dev.io/oauth/token"
            const val DEVEL = "https://login.confluent-dev.io/oauth/token"
        }

        object ClientId {
            const val PROD = "Q93zdbI3FnltpEa9G1gg6tiMuoDDBkwS"
            const val STAG = "S5PWFB5AQoLRg7fmsCxtBrGhYwTTzmAu"
            const val DEVEL = "cUmAgrkbAZSqSiy38JE7Ya3i7FwXmyUF"
        }

        const val SCOPE = "email openid offline_access"
        const val LOGIN_REALM_URI = "https://$BASE_PATH/api/login/realm"
    }

    object Token {
        const val CONTROL_PLANE_EXCHANGE_URI = "https://$BASE_PATH/api/sessions"
        const val CONTROL_PLANE_CHECK_JWT_URI = "https://$BASE_PATH/api/check_jwt"
        const val DATA_PLANE_EXCHANGE_URI = "https://$BASE_PATH/api/access_tokens"
        
        const val CONTROL_PLANE_LIFETIME_SECONDS = 300
        const val ID_TOKEN_LIFETIME_SECONDS = 60
        const val REFRESH_TOKEN_ABSOLUTE_LIFETIME_SECONDS = 28800
        const val MAX_REFRESH_ATTEMPTS = 50
    }

    object HttpClient {
        const val CONNECT_TIMEOUT_SECONDS = 10
        const val REQUEST_TIMEOUT_SECONDS = 30
    }

    object Pagination {
        const val DEFAULT_PAGE_SIZE = 100
        const val DEFAULT_MAX_PAGES = 100
    }

    object Refresh {
        const val STATUS_INTERVAL_SECONDS = 5
        const val TOKEN_EXPIRATION_CHECK_INTERVAL_SECONDS = 5
    }
}

