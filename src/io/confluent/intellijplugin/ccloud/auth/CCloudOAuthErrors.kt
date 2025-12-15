package io.confluent.intellijplugin.ccloud.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

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
