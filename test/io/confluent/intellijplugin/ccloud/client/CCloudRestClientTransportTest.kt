package io.confluent.intellijplugin.ccloud.client

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.ccloud.auth.CCloudAuthService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.net.ConnectException
import java.net.ServerSocket
import kotlin.time.Duration.Companion.seconds

/** Tests for the shared HTTP transport behind fetch / fetchList / executeRequest. */
@TestApplication
class CCloudRestClientTransportTest {

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

        private fun fixture(name: String): String =
            CCloudRestClientTransportTest::class.java.getResourceAsStream("/fixtures/ccloud/$name")!!
                .bufferedReader().use { it.readText() }

        /** A localhost port with nothing listening on it. */
        private fun closedPort(): Int = ServerSocket(0).use { it.localPort }
    }

    private lateinit var authService: CCloudAuthService
    private lateinit var client: CCloudRestClient

    // Single attempt: these tests cover the transport, retries are covered by CCloudRestClientRetryTest.
    // Default Retry-After clamp (60s) is kept so header parsing is exercised as in production.
    private fun clientFor(baseUrl: String) = CCloudRestClient(
        baseUrl = baseUrl,
        authType = CCloudRestClient.AuthType.DATA_PLANE,
        authService = authService,
        retryPolicy = CCloudRetryPolicy(maxAttempts = 1)
    )

    @BeforeEach
    fun setup() {
        authService = mock()
        whenever(authService.isSignedIn()).thenReturn(true)
        whenever(authService.getDataPlaneToken()).thenReturn("test-token")
        client = clientFor("http://localhost:${wireMockServer.port()}")
    }

    @AfterEach
    fun cleanUp() {
        wireMockServer.resetAll()
    }

    @Nested
    inner class ErrorResponses {

        @Test
        fun `fetch should surface error body message and status`() = runBlocking {
            wireMockServer.stubFor(
                get(urlEqualTo("/subjects/orders-value/versions/latest"))
                    .willReturn(aResponse().withStatus(404).withBody(fixture("error-subject-not-found.json")))
            )

            val exception = assertThrows<CCloudApiException> {
                client.fetch("/subjects/orders-value/versions/latest") { it }
            }

            assertEquals(404, exception.statusCode)
            assertEquals("Subject 'orders-value' not found. (HTTP 404)", exception.message)
            assertNull(exception.retryAfter)
        }

        @Test
        fun `fetchList should surface error body message and status`() = runBlocking {
            wireMockServer.stubFor(
                get(urlEqualTo("/topics"))
                    .willReturn(aResponse().withStatus(503).withBody(fixture("error-service-unavailable.json")))
            )

            val exception = assertThrows<CCloudApiException> {
                client.fetchList<String>("/topics") { emptyList<String>() to null }
            }

            assertEquals(503, exception.statusCode)
            assertEquals("Service temporarily unavailable (HTTP 503)", exception.message)
        }

        @Test
        fun `executeRequest should surface error body message and status`() = runBlocking {
            wireMockServer.stubFor(
                post(urlEqualTo("/records"))
                    .willReturn(aResponse().withStatus(404).withBody(fixture("error-subject-not-found.json")))
            )

            val exception = assertThrows<CCloudApiException> {
                client.executeRequest("/records", "POST", "{}")
            }

            assertEquals(404, exception.statusCode)
            assertEquals("Subject 'orders-value' not found. (HTTP 404)", exception.message)
        }
    }

    @Nested
    inner class RetryAfterHeader {

        @Test
        fun `should expose Retry-After delta-seconds on fetch errors`() = runBlocking {
            wireMockServer.stubFor(
                get(urlEqualTo("/test"))
                    .willReturn(
                        aResponse().withStatus(503)
                            .withHeader("Retry-After", "7")
                            .withBody(fixture("error-service-unavailable.json"))
                    )
            )

            val exception = assertThrows<CCloudApiException> { client.fetch("/test") { it } }

            assertEquals(503, exception.statusCode)
            assertEquals(7.seconds, exception.retryAfter)
        }

        @Test
        fun `should expose Retry-After on executeRequest errors`() = runBlocking {
            wireMockServer.stubFor(
                post(urlEqualTo("/records"))
                    .willReturn(
                        aResponse().withStatus(503)
                            .withHeader("Retry-After", "3")
                            .withBody(fixture("error-service-unavailable.json"))
                    )
            )

            val exception = assertThrows<CCloudApiException> { client.executeRequest("/records", "POST", "{}") }

            assertEquals(3.seconds, exception.retryAfter)
        }

        @Test
        fun `should clamp Retry-After to policy maximum`() = runBlocking {
            wireMockServer.stubFor(
                get(urlEqualTo("/test"))
                    .willReturn(aResponse().withStatus(503).withHeader("Retry-After", "3600"))
            )

            val exception = assertThrows<CCloudApiException> { client.fetch("/test") { it } }

            assertEquals(60.seconds, exception.retryAfter)
        }

        @Test
        fun `should ignore unparseable Retry-After`() = runBlocking {
            wireMockServer.stubFor(
                get(urlEqualTo("/test"))
                    .willReturn(aResponse().withStatus(503).withHeader("Retry-After", "soon"))
            )

            val exception = assertThrows<CCloudApiException> { client.fetch("/test") { it } }

            assertNull(exception.retryAfter)
        }
    }

    @Nested
    inner class ParseErrors {

        @Test
        fun `fetch parse failure should propagate parser exception rather than status 0`() = runBlocking {
            wireMockServer.stubFor(
                get(urlEqualTo("/test")).willReturn(aResponse().withStatus(200).withBody(fixture("simple-ok.json")))
            )

            val exception = assertThrows<IllegalStateException> {
                client.fetch<String>("/test") { throw IllegalStateException("bad payload") }
            }

            assertEquals("bad payload", exception.message)
            wireMockServer.verify(1, getRequestedFor(urlEqualTo("/test")))
        }
    }

    @Nested
    inner class Requests {

        @Test
        fun `executeRequest should send method, body and auth header`() = runBlocking {
            wireMockServer.stubFor(
                post(urlEqualTo("/records")).willReturn(aResponse().withStatus(200).withBody(fixture("simple-ok.json")))
            )

            val response = client.executeRequest("/records", "POST", """{"k":"v"}""")

            assertEquals(fixture("simple-ok.json"), response)
            wireMockServer.verify(
                postRequestedFor(urlEqualTo("/records"))
                    .withHeader("Authorization", equalTo("Bearer test-token"))
                    .withRequestBody(equalTo("""{"k":"v"}"""))
            )
        }

        @Test
        fun `fetch should return success body`() = runBlocking {
            wireMockServer.stubFor(
                get(urlEqualTo("/test")).willReturn(aResponse().withStatus(200).withBody(fixture("simple-ok.json")))
            )

            assertEquals(fixture("simple-ok.json"), client.fetch("/test") { it })
        }
    }

    @Nested
    inner class ConnectFailures {

        @Test
        fun `connection refused should raise CCloudConnectException with status 0`() = runBlocking {
            val refused = clientFor("http://localhost:${closedPort()}")

            val exception = assertThrows<CCloudApiException> { refused.fetch("/test") { it } }

            assertInstanceOf(CCloudConnectException::class.java, exception)
            assertEquals(0, exception.statusCode)
            assertInstanceOf(ConnectException::class.java, exception.cause)
        }

        @Test
        fun `connection refused on executeRequest should raise CCloudConnectException`() = runBlocking {
            val refused = clientFor("http://localhost:${closedPort()}")

            val exception = assertThrows<CCloudApiException> { refused.executeRequest("/records", "POST", "{}") }

            assertInstanceOf(CCloudConnectException::class.java, exception)
        }

        @Test
        fun `unknown host should raise CCloudConnectException`() = runBlocking {
            val unknown = clientFor("http://does-not-exist.invalid")

            val exception = assertThrows<CCloudApiException> { unknown.fetch("/test") { it } }

            assertInstanceOf(CCloudConnectException::class.java, exception)
            assertEquals(0, exception.statusCode)
        }

        @Test
        fun `HTTP errors should not be CCloudConnectException`() = runBlocking {
            wireMockServer.stubFor(get(urlEqualTo("/test")).willReturn(aResponse().withStatus(500)))

            val exception = assertThrows<CCloudApiException> { client.fetch("/test") { it } }

            assertFalse(exception is CCloudConnectException)
            assertEquals(500, exception.statusCode)
        }
    }
}
