package io.confluent.intellijplugin.registry.confluent

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClientConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ConfluentRegistryClientPropsTest {

    @Nested
    inner class `default http props` {

        @Test
        fun `should set explicit timeouts and retries`() {
            val defaults = ConfluentRegistryClient.DEFAULT_HTTP_PROPS

            assertEquals(
                SchemaRegistryClientConfig.HTTP_CONNECT_TIMEOUT_MS_DEFAULT.toString(),
                defaults[SchemaRegistryClientConfig.HTTP_CONNECT_TIMEOUT_MS]
            )
            assertEquals(
                SchemaRegistryClientConfig.HTTP_READ_TIMEOUT_MS_DEFAULT.toString(),
                defaults[SchemaRegistryClientConfig.HTTP_READ_TIMEOUT_MS]
            )
            assertEquals(
                SchemaRegistryClientConfig.MAX_RETRIES_DEFAULT.toString(),
                defaults[SchemaRegistryClientConfig.MAX_RETRIES_CONFIG]
            )
        }

        @Test
        fun `should be parsed by the schema registry client config`() {
            val props = ConfluentRegistryClient.mergeProps(emptyMap(), emptyMap(), emptyMap())

            assertEquals(
                SchemaRegistryClientConfig.HTTP_CONNECT_TIMEOUT_MS_DEFAULT,
                SchemaRegistryClientConfig.getHttpConnectTimeoutMs(props)
            )
            assertEquals(
                SchemaRegistryClientConfig.HTTP_READ_TIMEOUT_MS_DEFAULT,
                SchemaRegistryClientConfig.getHttpReadTimeoutMs(props)
            )
            assertEquals(SchemaRegistryClientConfig.MAX_RETRIES_DEFAULT, SchemaRegistryClientConfig.getMaxRetries(props))
        }
    }

    @Nested
    inner class `mergeProps` {

        @Test
        fun `should keep defaults when nothing else is configured`() {
            val props = ConfluentRegistryClient.mergeProps(mapOf("a" to "1"), mapOf("b" to "2"), mapOf("c" to "3"))

            assertEquals("1", props["a"])
            assertEquals("2", props["b"])
            assertEquals("3", props["c"])
            assertEquals("60000", props[SchemaRegistryClientConfig.HTTP_READ_TIMEOUT_MS])
        }

        @Test
        fun `should let user registry properties override the defaults`() {
            val props = ConfluentRegistryClient.mergeProps(
                emptyMap(),
                emptyMap(),
                mapOf(
                    SchemaRegistryClientConfig.HTTP_READ_TIMEOUT_MS to "5000",
                    SchemaRegistryClientConfig.MAX_RETRIES_CONFIG to "0",
                )
            )

            assertEquals("5000", props[SchemaRegistryClientConfig.HTTP_READ_TIMEOUT_MS])
            assertEquals("0", props[SchemaRegistryClientConfig.MAX_RETRIES_CONFIG])
            assertEquals("60000", props[SchemaRegistryClientConfig.HTTP_CONNECT_TIMEOUT_MS])
        }

        @Test
        fun `should let broker settings override the defaults`() {
            val props = ConfluentRegistryClient.mergeProps(
                mapOf(SchemaRegistryClientConfig.HTTP_CONNECT_TIMEOUT_MS to "1234"), emptyMap(), emptyMap()
            )

            assertEquals("1234", props[SchemaRegistryClientConfig.HTTP_CONNECT_TIMEOUT_MS])
        }
    }
}
