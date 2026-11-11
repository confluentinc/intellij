package io.confluent.intellijplugin.client

import com.intellij.testFramework.junit5.TestApplication
import org.apache.kafka.clients.admin.AdminClientConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.Properties

@TestApplication
class KafkaClientBuilderTest {

    private fun connectionProps(vararg extra: Pair<String, String>) = Properties().apply {
        // Nothing listens here; creating an admin client doesn't connect.
        this[AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG] = "localhost:1"
        extra.forEach { (key, value) -> this[key] = value }
    }

    private fun clientIdOf(properties: Properties): String {
        val admin = KafkaClientBuilder.createAdminClient(properties)
        try {
            return admin.metrics().keys.firstNotNullOf { it.tags()["client-id"] }
        } finally {
            admin.close(Duration.ZERO)
        }
    }

    @Test
    fun `should not add the admin client id to the shared connection properties`() {
        // The producer and consumer copy these later and add their own client.id only if none is set.
        val props = connectionProps()

        clientIdOf(props)

        assertFalse(props.containsKey(AdminClientConfig.CLIENT_ID_CONFIG))
    }

    @Test
    fun `should give the admin client the plugin admin client id`() {
        val clientId = clientIdOf(connectionProps())

        assertTrue(clientId.endsWith(" - Admin"), clientId)
    }

    @Test
    fun `should keep a client id set on the connection`() {
        val clientId = clientIdOf(connectionProps(AdminClientConfig.CLIENT_ID_CONFIG to "my-app"))

        assertEquals("my-app", clientId)
    }
}
