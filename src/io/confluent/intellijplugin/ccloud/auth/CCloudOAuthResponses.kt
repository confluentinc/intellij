package io.confluent.intellijplugin.ccloud.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

/** Auth0 /oauth/token response */
@Serializable
@JsonIgnoreUnknownKeys
data class IdTokenExchangeResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("id_token") val idToken: String? = null,
    val scope: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("token_type") val tokenType: String? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: JsonElement? = null
)

/**
 * Control plane /api/sessions response.
 * The actual auth token is in the Set-Cookie header (auth_token), use withToken() to set it after extracting from cookie.
 */
@Serializable
@JsonIgnoreUnknownKeys
data class ControlPlaneTokenExchangeResponse(
    val token: String? = null,  // Set via withToken() from cookie
    val error: JsonElement? = null,
    val user: UserDetails? = null,
    val organization: OrganizationDetails? = null,
    @SerialName("identity_provider") val identityProvider: JsonElement? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
) {
    fun withToken(token: String) = copy(token = token)
}

/** Data plane /api/access_tokens response */
@Serializable
@JsonIgnoreUnknownKeys
data class DataPlaneTokenExchangeResponse(
    val token: String? = null,
    val error: JsonElement? = null,
    @SerialName("regional_token") val regionalToken: String? = null,
)

/** Check JWT /api/check_jwt response */
@Serializable
@JsonIgnoreUnknownKeys
data class CheckJwtResponse(
    val error: JsonElement? = null,
    val claims: JsonElement? = null,
)
