package io.confluent.intellijplugin.ccloud.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

/** The three HTTP calls that make up a sign-in or a token refresh, in the order they run. */
enum class TokenEndpoint(val path: String) {
    /** Auth0 `oauth/token`: authorization code or refresh token -> ID token + new refresh token. */
    OAUTH_TOKEN("oauth/token"),
    /** CCloud `api/sessions`: ID token -> control plane token. */
    CONTROL_PLANE("api/sessions"),
    /** CCloud `api/access_tokens`: control plane token -> data plane token. */
    DATA_PLANE("api/access_tokens"),
}

/**
 * A token endpoint answered with an error. Keeps the HTTP status (and `Retry-After`, if the server
 * sent one) so callers can decide whether trying again could help.
 *
 * @param endpoint Which of the token calls failed
 * @param httpStatusCode The HTTP status of the response
 * @param retryAfterHeader Raw `Retry-After` header value, if present
 * @see TokenRefreshErrorClassifier
 */
open class TokenEndpointException(
    val endpoint: TokenEndpoint,
    val httpStatusCode: Int,
    val retryAfterHeader: String? = null,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * The context holds no refresh token, so a refresh cannot even be attempted. Terminal: trying
 * again will not produce one; only a new sign-in will.
 */
class MissingRefreshTokenException : IllegalStateException("Refresh token is missing.")

/**
 * Exception thrown when an OAuth token exchange fails.
 * Preserves the HTTP status code from the OAuth server response.
 *
 * @param errorCode The OAuth error code (e.g., "invalid_grant", "invalid_request")
 * @param errorDescription The readable error description from the OAuth server
 * @param httpStatusCode The HTTP status code from the OAuth server (typically 400 for OAuth errors)
 * @param retryAfterHeader Raw `Retry-After` header value, if present
 */
class OAuthErrorException(
    val errorCode: String,
    val errorDescription: String?,
    httpStatusCode: Int,
    retryAfterHeader: String? = null,
) : TokenEndpointException(
    endpoint = TokenEndpoint.OAUTH_TOKEN,
    httpStatusCode = httpStatusCode,
    retryAfterHeader = retryAfterHeader,
    message = "$errorCode: ${errorDescription ?: "Unknown error"}"
)

/**
 * Decides whether a failed token refresh is worth retrying.
 *
 * Any 4xx from a token endpoint other than 408 (Request Timeout) and 429 (Too Many Requests) is
 * final: the refresh token, ID token or control plane token was rejected, and sending the same
 * request again will not change that. A missing refresh token is also final. Everything else
 * (5xx, 408, 429, I/O errors, unparseable bodies, anything untyped) is treated as transient.
 */
internal object TokenRefreshErrorClassifier {
    const val HTTP_REQUEST_TIMEOUT = 408
    const val HTTP_TOO_MANY_REQUESTS = 429

    /**
     * RFC 6749 §5.2 error codes. None of them can succeed on a retry of the same request. Checked
     * in addition to the status so an OAuth error body is final even if it arrives with an
     * unexpected status. `invalid_grant` is what Auth0 returns for "Unknown or invalid refresh token.".
     */
    private val TERMINAL_OAUTH_ERROR_CODES = setOf(
        "invalid_request",
        "invalid_client",
        "invalid_grant",
        "unauthorized_client",
        "unsupported_grant_type",
        "invalid_scope",
    )

    fun isTerminalStatus(httpStatusCode: Int): Boolean =
        httpStatusCode in 400..499 &&
            httpStatusCode != HTTP_REQUEST_TIMEOUT &&
            httpStatusCode != HTTP_TOO_MANY_REQUESTS

    fun isTerminal(error: Throwable): Boolean = when (error) {
        is OAuthErrorException -> when (error.httpStatusCode) {
            HTTP_REQUEST_TIMEOUT, HTTP_TOO_MANY_REQUESTS -> false
            else -> isTerminalStatus(error.httpStatusCode) || error.errorCode in TERMINAL_OAUTH_ERROR_CODES
        }
        is TokenEndpointException -> isTerminalStatus(error.httpStatusCode)
        is MissingRefreshTokenException -> true
        else -> false
    }

    /** The `Retry-After` header to honor before the next try: only for 429 responses. */
    fun retryAfterHeader(error: Throwable): String? =
        (error as? TokenEndpointException)
            ?.takeIf { it.httpStatusCode == HTTP_TOO_MANY_REQUESTS }
            ?.retryAfterHeader
}

/**
 * This record holds Confluent Cloud OAuth-related errors.
 * @see CCloudOAuthContext
 *
 * @param authStatusCheck Error that occurred when checking the auth status.
 * @param signIn          Error that occurred when performing the sign in.
 * @param tokenRefresh    Error that occurred when refreshing tokens.
 */
@Serializable
@JsonIgnoreUnknownKeys
data class AuthErrors(
    @SerialName("auth_status_check") val authStatusCheck: AuthError? = null,
    @SerialName("sign_in") val signIn: AuthError? = null,
    @SerialName("token_refresh") val tokenRefresh: AuthError? = null
) {
    @Serializable
    data class AuthError(
        val message: String,
        @SerialName("is_transient") val isTransient: Boolean
    )

    fun hasErrors() = authStatusCheck != null || tokenRefresh != null || signIn != null

    fun withSignIn(message: String) = copy(signIn = AuthError(message, isTransient = false))
    fun withoutSignIn() = copy(signIn = null)

    fun withTokenRefresh(message: String, isTransient: Boolean) = copy(tokenRefresh = AuthError(message, isTransient))
    fun withoutTokenRefresh() = copy(tokenRefresh = null)

    fun withAuthStatusCheck(message: String) = copy(authStatusCheck = AuthError(message, true))
    fun withoutAuthStatusCheck() = copy(authStatusCheck = null)

    fun hasNonTransientErrors(): Boolean =
        signIn?.isTransient == false || tokenRefresh?.isTransient == false || authStatusCheck?.isTransient == false
}
