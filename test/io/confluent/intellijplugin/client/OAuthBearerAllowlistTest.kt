package io.confluent.intellijplugin.client

import org.apache.kafka.common.config.internals.BrokerSecurityConfigs
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class OAuthBearerAllowlistTest {
    private val property = BrokerSecurityConfigs.ALLOWED_SASL_OAUTHBEARER_URLS_CONFIG
    private var original: String? = null

    @BeforeEach
    fun saveAndClear() {
        original = System.getProperty(property)
        System.clearProperty(property)
    }

    @AfterEach
    fun restore() {
        original?.let { System.setProperty(property, it) } ?: System.clearProperty(property)
    }

    private fun allowed(): String? = System.getProperty(property)

    @Nested
    inner class `adding urls` {
        @Test
        fun `should allow registry issuer url`() {
            OAuthBearerAllowlist.allowUrls(mapOf("bearer.auth.issuer.endpoint.url" to "https://idp.example.com/token"))

            assertEquals("https://idp.example.com/token", allowed())
        }

        @Test
        fun `should allow broker token and jwks endpoint urls`() {
            OAuthBearerAllowlist.allowUrls(
                mapOf(
                    "sasl.oauthbearer.token.endpoint.url" to "https://idp.example.com/token",
                    "sasl.oauthbearer.jwks.endpoint.url" to "http://idp.example.com/jwks",
                )
            )

            assertEquals("https://idp.example.com/token,http://idp.example.com/jwks", allowed())
        }

        @Test
        fun `should trim whitespace around the url`() {
            OAuthBearerAllowlist.allowUrls(mapOf("bearer.auth.issuer.endpoint.url" to "  https://idp.example.com/token \n"))

            assertEquals("https://idp.example.com/token", allowed())
        }

        @Test
        fun `should read java Properties`() {
            val props = java.util.Properties().apply {
                setProperty("sasl.oauthbearer.token.endpoint.url", "https://idp.example.com/token")
            }

            OAuthBearerAllowlist.allowUrls(props)

            assertEquals("https://idp.example.com/token", allowed())
        }
    }

    @Nested
    inner class `existing entries` {
        @Test
        fun `should preserve entries already set`() {
            System.setProperty(property, "https://existing.example.com/token")

            OAuthBearerAllowlist.allowUrls(mapOf("bearer.auth.issuer.endpoint.url" to "https://idp.example.com/token"))

            assertEquals("https://existing.example.com/token,https://idp.example.com/token", allowed())
        }

        @Test
        fun `should not duplicate a url that is already allowed`() {
            val props = mapOf("bearer.auth.issuer.endpoint.url" to "https://idp.example.com/token")

            OAuthBearerAllowlist.allowUrls(props)
            OAuthBearerAllowlist.allowUrls(props)

            assertEquals("https://idp.example.com/token", allowed())
        }
    }

    @Nested
    inner class `ignored values` {
        @Test
        fun `should leave the property unset when no oauth keys are present`() {
            OAuthBearerAllowlist.allowUrls(mapOf("sasl.mechanism" to "PLAIN", "bootstrap.servers" to "localhost:9092"))

            assertEquals(null, allowed())
        }

        @Test
        fun `should ignore blank and non-string values`() {
            OAuthBearerAllowlist.allowUrls(
                mapOf(
                    "bearer.auth.issuer.endpoint.url" to "   ",
                    "sasl.oauthbearer.token.endpoint.url" to 42,
                )
            )

            assertEquals(null, allowed())
        }

        @Test
        fun `should never allow file urls`() {
            OAuthBearerAllowlist.allowUrls(mapOf("sasl.oauthbearer.jwks.endpoint.url" to "file:///etc/passwd"))

            assertEquals(null, allowed())
        }

        @Test
        fun `should ignore urls without an http scheme or that are malformed`() {
            OAuthBearerAllowlist.allowUrls(
                mapOf(
                    "bearer.auth.issuer.endpoint.url" to "ftp://idp.example.com/token",
                    "bearer.auth.uami.endpoint.url" to "not a url",
                    "sasl.oauthbearer.token.endpoint.url" to "idp.example.com/token",
                )
            )

            assertEquals(null, allowed())
        }

        @Test
        fun `should ignore urls containing a comma`() {
            OAuthBearerAllowlist.allowUrls(
                mapOf("bearer.auth.issuer.endpoint.url" to "https://idp.example.com/a,file:///etc/passwd")
            )

            assertEquals(null, allowed())
        }

        @Test
        fun `should ignore unrelated keys that hold urls`() {
            OAuthBearerAllowlist.allowUrls(mapOf("schema.registry.url" to "https://sr.example.com"))

            assertEquals(null, allowed())
        }
    }
}
