package io.confluent.intellijplugin.ccloud.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement


/** Auth0 /oauth/token response */
@Serializable
data class IdTokenExchangeResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("id_token") val idToken: String,
    val scope: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("token_type") val tokenType: String,
    val error: String,
    @SerialName("error_description") val errorDescription: JsonElement
)

/** Control plane /api/sessions response */
@Serializable
data class ControlPlaneTokenExchangeResponse(
    val token: String,
    val error: JsonElement,
    val user: UserDetails,
    val organization: OrganizationDetails,
    @SerialName("identity_provider") val identityProvider: JsonElement,
    @SerialName("refresh_token") val refreshToken: String,
) {
    fun withToken(token: String) = copy(token = token)
}

/** Data plane /api/access_tokens response */
@Serializable
data class DataPlaneTokenExchangeResponse(
    val token: String,
    val error: JsonElement,
    @SerialName("regional_token") val regionalToken: String,
)

@Serializable
data class CheckJwtResponse(
    val error: JsonElement,
    val claims: JsonElement,
)
