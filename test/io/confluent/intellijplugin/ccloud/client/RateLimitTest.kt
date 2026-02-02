package io.confluent.intellijplugin.ccloud.client

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.ccloud.auth.CCloudAuthService
import kotlinx.coroutines.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@TestApplication
class RateLimitTest {

    companion object {
        private lateinit var wireMockServer: WireMockServer
        private const val TEST_TOKEN = "test-token"

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

    private lateinit var client: CCloudRestClient
    private lateinit var authService: CCloudAuthService

    @BeforeEach
    fun setup() {
        authService = mock()
        whenever(authService.isSignedIn()).thenReturn(true)
        whenever(authService.getDataPlaneToken()).thenReturn(TEST_TOKEN)

        client = CCloudRestClient(
            baseUrl = "http://localhost:${wireMockServer.port()}",
            authType = CCloudRestClient.AuthType.DATA_PLANE,
            authService = authService
        )
    }

    @AfterEach
    fun cleanUp() {
        wireMockServer.resetAll()
    }

    @Nested
    @DisplayName("Rate Limit - 429 Retry Logic")
    inner class RetryOn429Tests {

        @Test
        fun `retries on 429 error and succeeds on second attempt`() = runBlocking {
            // First request: 429
            wireMockServer.stubFor(
                get(urlEqualTo("/test"))
                    .inScenario("Retry")
                    .whenScenarioStateIs("Started")
                    .willReturn(aResponse().withStatus(429).withBody("Rate limit exceeded"))
                    .willSetStateTo("FirstAttempt")
            )

            // Second request: 200 OK
            wireMockServer.stubFor(
                get(urlEqualTo("/test"))
                    .inScenario("Retry")
                    .whenScenarioStateIs("FirstAttempt")
                    .willReturn(aResponse().withStatus(200).withBody("Success"))
            )

            val result = client.fetch("/test") { it }

            assertEquals("Success", result)
            wireMockServer.verify(2, getRequestedFor(urlEqualTo("/test")))
        }

        @Test
        fun `throws exception after max retries`() = runBlocking {
            // Always return 429
            wireMockServer.stubFor(
                get(urlEqualTo("/test"))
                    .willReturn(aResponse().withStatus(429).withBody("Rate limit exceeded"))
            )

            val exception = assertThrows<CCloudApiException> {
                client.fetch("/test") { it }
            }

            assertEquals(429, exception.statusCode)
            // Should try 4 times (initial + 3 retries)
            wireMockServer.verify(4, getRequestedFor(urlEqualTo("/test")))
        }

        @Test
        fun `does not retry on non-429 errors`() = runBlocking {
            wireMockServer.stubFor(
                get(urlEqualTo("/test"))
                    .willReturn(aResponse().withStatus(500).withBody("Internal Server Error"))
            )

            val exception = assertThrows<CCloudApiException> {
                client.fetch("/test") { it }
            }

            assertEquals(500, exception.statusCode)
            // Should only try once (no retries for non-429)
            wireMockServer.verify(1, getRequestedFor(urlEqualTo("/test")))
        }
    }

}
