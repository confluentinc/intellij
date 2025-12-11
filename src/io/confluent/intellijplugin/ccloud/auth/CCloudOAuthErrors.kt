package io.confluent.intellijplugin.ccloud.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Confluent Cloud OAuth errors */
@Serializable
data class AuthErrors(
    @SerialName("auth_status_check") val signIn: AuthError? = null,
    @SerialName("sign_in") val tokenRefresh: AuthError? = null,
    @SerialName("token_refresh") val authStatusCheck: AuthError? = null
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
