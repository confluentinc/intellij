package io.confluent.intellijplugin.ccloud.auth

import io.confluent.intellijplugin.ccloud.util.ResourceLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.Base64
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CCloudJwtTest {

    companion object {
        private val encoder = Base64.getUrlEncoder().withoutPadding()

        private fun segment(text: String): String = encoder.encodeToString(text.toByteArray())

        /** A JWT with the given payload; the signature is never checked. */
        private fun jwt(payload: String): String = "${segment("""{"alg":"RS256","typ":"JWT"}""")}.${segment(payload)}.c2ln"

        @JvmStatic
        fun tokensWithoutUsableLifetime() = listOf(
            Arguments.of("opaque token", "test_cp_token"),
            Arguments.of("payload is not base64url", "${segment("{}")}.!!!.c2ln"),
            Arguments.of("payload is not JSON", "${segment("{}")}.${segment("not json")}.c2ln"),
            Arguments.of("payload is a JSON array", jwt("""[1300, 1000]""")),
            Arguments.of("exp is missing", jwt("""{"iat":1000}""")),
            Arguments.of("iat is missing", jwt("""{"exp":1300}""")),
            Arguments.of("exp is not a number", jwt("""{"iat":1000,"exp":"soon"}""")),
            Arguments.of("exp is before iat", jwt("""{"iat":1300,"exp":1000}""")),
            Arguments.of("exp equals iat", jwt("""{"iat":1000,"exp":1000}""")),
        )
    }

    @Nested
    @DisplayName("lifetime")
    inner class Lifetime {

        @Test
        fun `is exp minus iat`() {
            assertEquals(300.seconds, CCloudJwt.lifetime(jwt("""{"iat":1000,"exp":1300,"sub":"u-1"}""")))
        }

        @Test
        fun `keeps fractional seconds, which RFC 7519 NumericDate allows`() {
            assertEquals(300.seconds + 500.milliseconds, CCloudJwt.lifetime(jwt("""{"iat":1000,"exp":1300.5}""")))
        }

        @Test
        fun `reads the real-shaped control-plane and data-plane fixtures`() {
            val controlPlane = ResourceLoader.loadResource("fixtures/ccloud-auth/control-plane-auth-token-300s.jwt").trim()
            val dataPlane = CCloudOAuthHttpClient.json.decodeFromString<DataPlaneTokenExchangeResponse>(
                ResourceLoader.loadResource("fixtures/ccloud-auth/data-plane-token-900s.json")
            ).token!!

            assertEquals(300.seconds, CCloudJwt.lifetime(controlPlane))
            assertEquals(900.seconds, CCloudJwt.lifetime(dataPlane))
        }

        @ParameterizedTest(name = "null when {0}")
        @MethodSource("io.confluent.intellijplugin.ccloud.auth.CCloudJwtTest#tokensWithoutUsableLifetime")
        fun `is null without a usable exp and iat`(@Suppress("UNUSED_PARAMETER") case: String, token: String) {
            assertNull(CCloudJwt.lifetime(token))
        }
    }
}
