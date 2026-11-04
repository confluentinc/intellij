package io.confluent.intellijplugin.ccloud.client

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.testutil.wireMockExtension
import kotlinx.coroutines.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
class RateLimitTest {

    companion object {
        @JvmField
        @RegisterExtension
        val wireMock = wireMockExtension()
    }

    private lateinit var client: CCloudRestClient

    @BeforeEach
    fun setup() {
        client = testClient(wireMock.baseUrl())
    }

    @Nested
    @DisplayName("Rate Limit - 429 Retry Logic")
    inner class RetryOn429Tests {

        @Test
        fun `retries on 429 error and succeeds on second attempt`() = runBlocking {
            // First request: 429
            wireMock.stubFor(
                get(urlEqualTo("/test"))
                    .inScenario("Retry")
                    .whenScenarioStateIs("Started")
                    .willReturn(aResponse().withStatus(429).withBody("Rate limit exceeded"))
                    .willSetStateTo("FirstAttempt")
            )

            // Second request: 200 OK
            wireMock.stubFor(
                get(urlEqualTo("/test"))
                    .inScenario("Retry")
                    .whenScenarioStateIs("FirstAttempt")
                    .willReturn(aResponse().withStatus(200).withBody("Success"))
            )

            val result = client.fetch("/test") { it }

            assertEquals("Success", result)
            wireMock.verify(2, getRequestedFor(urlEqualTo("/test")))
        }

        @Test
        fun `throws exception after max retries`() = runBlocking {
            // Always return 429
            wireMock.stubFor(
                get(urlEqualTo("/test"))
                    .willReturn(aResponse().withStatus(429).withBody("Rate limit exceeded"))
            )

            val exception = assertThrows<CCloudApiException> {
                client.fetch("/test") { it }
            }

            assertEquals(429, exception.statusCode)
            // Should try 4 times (initial + 3 retries)
            wireMock.verify(4, getRequestedFor(urlEqualTo("/test")))
        }

        @Test
        fun `does not retry on non-429 errors`() = runBlocking {
            wireMock.stubFor(
                get(urlEqualTo("/test"))
                    .willReturn(aResponse().withStatus(500).withBody("Internal Server Error"))
            )

            val exception = assertThrows<CCloudApiException> {
                client.fetch("/test") { it }
            }

            assertEquals(500, exception.statusCode)
            // Should only try once (no retries for non-429)
            wireMock.verify(1, getRequestedFor(urlEqualTo("/test")))
        }
    }

}
