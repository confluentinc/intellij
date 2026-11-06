package io.confluent.intellijplugin.ccloud.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.util.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Reads the time claims of the CCloud JWTs: the control-plane `auth_token` and the data-plane token.
 *
 * The signature is not checked. The claims only decide when we refresh; the server that issued the
 * token still checks it on every request.
 */
internal object CCloudJwt {
    private val decoder = Base64.getUrlDecoder()

    /**
     * The token's lifetime, `exp − iat` ([RFC 7519 section 4.1.4](https://www.rfc-editor.org/rfc/rfc7519#section-4.1.4)).
     * Taken from the claims rather than `exp − local now`, so a skewed local clock doesn't change it.
     *
     * @return null if [token] isn't a JWT, `exp` or `iat` is missing or not a number, or `exp` isn't after `iat`
     */
    fun lifetime(token: String): Duration? {
        val payload = token.split('.').takeIf { it.size == 3 }?.get(1) ?: return null
        val claims = runCatching {
            Json.parseToJsonElement(String(decoder.decode(payload), Charsets.UTF_8)) as? JsonObject
        }.getOrNull() ?: return null

        val exp = claims.numericDate("exp") ?: return null
        val iat = claims.numericDate("iat") ?: return null
        return (exp - iat).takeIf { it > 0 }?.seconds
    }

    /** A NumericDate is a JSON number of seconds, possibly fractional; a quoted string isn't one. */
    private fun JsonObject.numericDate(name: String): Double? =
        (this[name] as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull
}
