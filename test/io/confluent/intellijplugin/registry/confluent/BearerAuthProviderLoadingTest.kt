package io.confluent.intellijplugin.registry.confluent

import io.confluent.intellijplugin.client.OAuthBearerAllowlist
import io.confluent.kafka.schemaregistry.client.security.bearerauth.BearerAuthCredentialProviderFactory
import org.apache.kafka.common.config.internals.BrokerSecurityConfigs
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Guards against kafka-clients / schema-registry-client version skew: the OAUTHBEARER provider
 * needs org.apache.kafka.common.security.oauthbearer.JwtRetriever, missing from kafka-clients < 4.1 (#637),
 * and Kafka refuses issuer URLs that are not in the allowed urls system property.
 */
class BearerAuthProviderLoadingTest {
    private val property = BrokerSecurityConfigs.ALLOWED_SASL_OAUTHBEARER_URLS_CONFIG
    private var original: String? = null

    @BeforeEach
    fun clearAllowedUrls() {
        original = System.getProperty(property)
        System.clearProperty(property)
    }

    @AfterEach
    fun restoreAllowedUrls() {
        original?.let { System.setProperty(property, it) } ?: System.clearProperty(property)
    }

    @Test
    fun `should load OAUTHBEARER credential provider for a configured issuer url`() {
        val config = mapOf(
            "bearer.auth.issuer.endpoint.url" to "https://localhost/oauth2/token",
            "bearer.auth.client.id" to "client",
            "bearer.auth.client.secret" to "secret",
            "bearer.auth.scope" to "scope",
            "bearer.auth.identity.pool.id" to "pool",
        )

        OAuthBearerAllowlist.allowUrls(config)
        val provider = BearerAuthCredentialProviderFactory.getBearerAuthCredentialProvider("OAUTHBEARER", config)

        assertNotNull(provider)
    }
}
