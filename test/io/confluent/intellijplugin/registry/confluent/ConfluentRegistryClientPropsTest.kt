package io.confluent.intellijplugin.registry.confluent

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import io.confluent.intellijplugin.registry.KafkaRegistryUtil
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClientConfig
import io.confluent.kafka.schemaregistry.client.rest.RestService
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

class ConfluentRegistryClientPropsTest {

    companion object {
        private lateinit var wireMockServer: WireMockServer

        @JvmStatic
        @BeforeAll
        fun setUpAll() {
            wireMockServer = WireMockServer(wireMockConfig().dynamicPort())
            wireMockServer.start()
        }

        @JvmStatic
        @AfterAll
        fun tearDownAll() {
            wireMockServer.stop()
        }
    }

    @AfterEach
    fun cleanUp() {
        wireMockServer.resetAll()
    }

    private fun restServiceWith(props: Map<String, String>) =
        RestService(wireMockServer.baseUrl()).also { it.configure(props) }

    @Nested
    inner class `default http props` {

        @Test
        fun `should set explicit timeouts and retries`() {
            val defaults = ConfluentRegistryClient.DEFAULT_HTTP_PROPS

            assertEquals("60000", defaults[SchemaRegistryClientConfig.HTTP_CONNECT_TIMEOUT_MS])
            assertEquals("60000", defaults[SchemaRegistryClientConfig.HTTP_READ_TIMEOUT_MS])
            assertEquals("3", defaults[SchemaRegistryClientConfig.MAX_RETRIES_CONFIG])
        }

        @Test
        fun `should be parsed by the schema registry client config`() {
            val props = ConfluentRegistryClient.mergeProps(emptyMap(), emptyMap(), emptyMap())

            assertEquals(60_000, SchemaRegistryClientConfig.getHttpConnectTimeoutMs(props))
            assertEquals(60_000, SchemaRegistryClientConfig.getHttpReadTimeoutMs(props))
            assertEquals(3, SchemaRegistryClientConfig.getMaxRetries(props))
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

    @Nested
    inner class `against a live endpoint` {

        @Test
        fun `should succeed when the server answers within the default read timeout`() {
            wireMockServer.stubFor(
                get(urlPathEqualTo("/subjects"))
                    .willReturn(aResponse().withStatus(200).withBody("[\"s1\"]").withFixedDelay(300))
            )

            val subjects = restServiceWith(ConfluentRegistryClient.mergeProps(emptyMap(), emptyMap(), emptyMap()))
                .getAllSubjects()

            assertEquals(listOf("s1"), subjects)
        }

        @Test
        fun `should fail with a timeout instead of hanging when the server is slower than the read timeout`() {
            wireMockServer.stubFor(
                get(urlPathEqualTo("/subjects"))
                    .willReturn(aResponse().withStatus(200).withBody("[]").withFixedDelay(3_000))
            )
            val props = ConfluentRegistryClient.mergeProps(
                emptyMap(), emptyMap(),
                mapOf(
                    SchemaRegistryClientConfig.HTTP_READ_TIMEOUT_MS to "300",
                    SchemaRegistryClientConfig.MAX_RETRIES_CONFIG to "0",
                )
            )

            val start = System.nanoTime()
            assertThrows(IOException::class.java) { restServiceWith(props).getAllSubjects() }
            val elapsedMs = (System.nanoTime() - start) / 1_000_000

            assertTrue(elapsedMs < 2_500, "read timeout was not applied, call took ${elapsedMs}ms")
        }
    }

    @Nested
    inner class `call timeout` {

        @Test
        fun `should give schema registry calls a 180 second budget`() {
            assertEquals(180.seconds, KafkaRegistryUtil.CALL_TIMEOUT)
        }
    }
}
