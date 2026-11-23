package io.confluent.intellijplugin.client

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.internals.BrokerSecurityConfigs
import org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginCallbackHandler
import org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import javax.security.auth.login.AppConfigurationEntry
import javax.security.auth.login.AppConfigurationEntry.LoginModuleControlFlag.REQUIRED

/**
 * Guards the broker-side OAUTHBEARER login (#637). Since kafka-clients 4.1 the login callback handler creates a
 * DefaultJwtValidator, which needs jose4j on the classpath. kafka-clients does not declare jose4j as a
 * dependency, so without our own declaration every OAUTHBEARER connection failed with
 * "DefaultJwtValidator ... could not be instantiated".
 */
class OAuthBearerLoginHandlerLoadingTest {
    private val property = BrokerSecurityConfigs.ALLOWED_SASL_OAUTHBEARER_URLS_CONFIG
    private var original: String? = null

    @BeforeEach
    fun allowTokenUrl() {
        original = System.getProperty(property)
        System.setProperty(property, TOKEN_URL)
    }

    @AfterEach
    fun restoreAllowedUrls() {
        original?.let { System.setProperty(property, it) } ?: System.clearProperty(property)
    }

    @Test
    fun `should configure OAUTHBEARER login handler for client credentials`() {
        // Real client config values, so class-typed defaults (jwt retriever/validator) are present as in the IDE.
        val configs = AdminClientConfig(
            mapOf(
                CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092",
                CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to "SASL_PLAINTEXT",
                SaslConfigs.SASL_MECHANISM to "OAUTHBEARER",
                SaslConfigs.SASL_OAUTHBEARER_TOKEN_ENDPOINT_URL to TOKEN_URL,
            )
        ).values()
        val jaas = listOf(
            AppConfigurationEntry(
                OAuthBearerLoginModule::class.java.name,
                REQUIRED,
                mapOf("clientId" to "client", "clientSecret" to "secret"),
            )
        )

        val handler = OAuthBearerLoginCallbackHandler()
        try {
            assertDoesNotThrow { handler.configure(configs, "OAUTHBEARER", jaas) }
        } finally {
            handler.close()
        }
    }

    private companion object {
        const val TOKEN_URL = "http://localhost:8080/realms/kafka/protocol/openid-connect/token"
    }
}
