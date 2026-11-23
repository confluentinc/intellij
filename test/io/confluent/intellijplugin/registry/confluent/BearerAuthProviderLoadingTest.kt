package io.confluent.intellijplugin.registry.confluent

import io.confluent.kafka.schemaregistry.client.security.bearerauth.BearerAuthCredentialProviderFactory
import org.apache.kafka.common.config.internals.BrokerSecurityConfigs
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Guards against kafka-clients / schema-registry-client version skew: the OAUTHBEARER provider
 * needs org.apache.kafka.common.security.oauthbearer.JwtRetriever, missing from kafka-clients < 4.1 (#637).
 */
class BearerAuthProviderLoadingTest {

    @BeforeEach
    fun allowIssuerUrl() {
        System.setProperty(BrokerSecurityConfigs.ALLOWED_SASL_OAUTHBEARER_URLS_CONFIG, ISSUER_URL)
    }

    @AfterEach
    fun clearAllowedUrls() {
        System.clearProperty(BrokerSecurityConfigs.ALLOWED_SASL_OAUTHBEARER_URLS_CONFIG)
    }

    @Test
    fun `should load OAUTHBEARER credential provider when kafka-clients matches schema registry client`() {
        val config = mapOf(
            "bearer.auth.issuer.endpoint.url" to ISSUER_URL,
            "bearer.auth.client.id" to "client",
            "bearer.auth.client.secret" to "secret",
            "bearer.auth.scope" to "scope",
            "bearer.auth.identity.pool.id" to "pool",
        )

        val provider = BearerAuthCredentialProviderFactory.getBearerAuthCredentialProvider("OAUTHBEARER", config)

        assertNotNull(provider)
    }

    private companion object {
        const val ISSUER_URL = "https://localhost/oauth2/token"
    }
}
