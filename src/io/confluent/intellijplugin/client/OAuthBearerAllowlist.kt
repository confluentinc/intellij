package io.confluent.intellijplugin.client

import com.intellij.openapi.diagnostic.thisLogger
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClientConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.internals.BrokerSecurityConfigs
import java.net.URI
import java.net.URISyntaxException

/**
 * Kafka rejects OAuth endpoint URLs that are not listed in the JVM property
 * `org.apache.kafka.sasl.oauthbearer.allowed.urls` (empty by default).
 *
 * The URLs come from the user's own connection settings, so they are added to that list before a client is
 * created. Entries are only ever added, never removed, so values the user set with `-D` are preserved.
 * Only `http`/`https` URLs are added: `file:` URLs would be checked against the allowed *files* list instead,
 * which this deliberately does not touch.
 */
object OAuthBearerAllowlist {
    private const val ALLOWED_URLS_PROPERTY = BrokerSecurityConfigs.ALLOWED_SASL_OAUTHBEARER_URLS_CONFIG
    private const val SEPARATOR = ","

    private val URL_KEYS = listOf(
        SchemaRegistryClientConfig.BEARER_AUTH_ISSUER_ENDPOINT_URL,
        SchemaRegistryClientConfig.BEARER_AUTH_UAMI_ENDPOINT_URL,
        SaslConfigs.SASL_OAUTHBEARER_TOKEN_ENDPOINT_URL,
        SaslConfigs.SASL_OAUTHBEARER_JWKS_ENDPOINT_URL,
    )
    private val ALLOWED_SCHEMES = setOf("http", "https")

    private val lock = Any()

    fun allowUrls(properties: Map<*, *>) {
        val urls = URL_KEYS.mapNotNull { (properties[it] as? String)?.trim() }.filter(::isAllowable)
        if (urls.isEmpty()) return

        synchronized(lock) {
            val current = System.getProperty(ALLOWED_URLS_PROPERTY, "")
                .split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }
            val added = urls.filter { it !in current }.distinct()
            if (added.isEmpty()) return

            System.setProperty(ALLOWED_URLS_PROPERTY, (current + added).joinToString(SEPARATOR))
            thisLogger().debug("Allowed OAuth endpoint URLs: $added")
        }
    }

    // The list is comma-separated, so a URL containing a comma could not be represented.
    private fun isAllowable(url: String): Boolean {
        if (url.isEmpty() || SEPARATOR in url) return false
        val scheme = try {
            URI(url).scheme
        } catch (_: URISyntaxException) {
            return false
        }
        return scheme?.lowercase() in ALLOWED_SCHEMES
    }
}
