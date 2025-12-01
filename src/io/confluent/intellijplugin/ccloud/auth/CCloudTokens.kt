package io.confluent.intellijplugin.ccloud.auth

import java.time.Instant

/** Token types for Confluent Cloud OAuth */
class CCloudTokens {

    // Models a Confluent Cloud API token.
    data class Token(val token: String, val expiresAt: Instant)

    // Auth0 response tokens
    data class Auth0(
        val accessToken: String,
        val refreshToken: String,
        val idToken: String,
        val expiresIn: Long
    )
}
