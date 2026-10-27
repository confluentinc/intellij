package io.confluent.intellijplugin.ccloud.client

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.http.Fault
import com.github.tomakehurst.wiremock.stubbing.Scenario
import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.ccloud.auth.CCloudAuthService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.net.ConnectException
import java.net.ServerSocket
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Retry behavior of [CCloudRestClient] driven by [CCloudRetryPolicy]. */
@TestApplication
class CCloudRestClientRetryTest {

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
            CCloudRestClientRetryTest::class.java.getResourceAsStream("/fixtures/ccloud/$name")!!
                .bufferedReader().use { it.readText() }

        private fun closedPort(): Int = ServerSocket(0).use { it.localPort }
    }

    /**
     * Counts jitter draws. [CCloudRetryPolicy.delayFor] draws exactly once per scheduled retry,
     * which lets us count retries against a closed port where WireMock can't count requests.
     */
    private class CountingRandom : Random() {
        var draws = 0
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextDouble(): Double {
            draws++
            return 0.0
        }
    }

    private lateinit var authService: CCloudAuthService
    private lateinit var client: CCloudRestClient

    private fun fastPolicy(random: Random = Random.Default, maxRetryAfter: Duration = 60.seconds) =
        CCloudRetryPolicy(
            initialBackoff = 1.milliseconds,
            maxBackoff = 1.milliseconds,
            jitter = 0.0,
            maxRetryAfter = maxRetryAfter,
            random = random
        )

    private fun clientFor(baseUrl: String, policy: CCloudRetryPolicy = fastPolicy()) = CCloudRestClient(
        baseUrl = baseUrl,
        authType = CCloudRestClient.AuthType.DATA_PLANE,
        authService = authService,
        retryPolicy = policy
    )

    private val wireMockUrl get() = "http://localhost:${wireMockServer.port()}"

    @BeforeEach
    fun setup() {
        authService = mock()
        whenever(authService.isSignedIn()).thenReturn(true)
        whenever(authService.getDataPlaneToken()).thenReturn("test-token")
        client = clientFor(wireMockUrl)
    }

    @AfterEach
    fun cleanUp() {
        wireMockServer.resetAll()
    }

    /** First call returns [status] (with optional Retry-After), subsequent calls return 200 with [okBody]. */
    private fun stubFailThenSucceed(
        request: () -> MappingBuilder,
        status: Int,
        errorFixture: String,
        okBody: String,
        retryAfter: String? = null
    ) {
        val failure = aResponse().withStatus(status).withBody(fixture(errorFixture))
        retryAfter?.let { failure.withHeader("Retry-After", it) }
        wireMockServer.stubFor(
            request().inScenario("retry").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(failure).willSetStateTo("recovered")
        )
        wireMockServer.stubFor(
            request().inScenario("retry").whenScenarioStateIs("recovered")
                .willReturn(aResponse().withStatus(200).withBody(okBody))
        )
    }

    @Nested
    inner class IdempotentGets {

        @Test
        fun `fetch should retry 503 and succeed`() = runBlocking {
            stubFailThenSucceed({ get(urlEqualTo("/test")) }, 503, "error-service-unavailable.json", fixture("simple-ok.json"))

            assertEquals(fixture("simple-ok.json"), client.fetch("/test") { it })
            wireMockServer.verify(2, getRequestedFor(urlEqualTo("/test")))
        }

        @ParameterizedTest
        @ValueSource(ints = [502, 504])
        fun `fetch should retry other gateway errors`(status: Int) = runBlocking {
            stubFailThenSucceed({ get(urlEqualTo("/test")) }, status, "error-service-unavailable.json", fixture("simple-ok.json"))

            assertEquals(fixture("simple-ok.json"), client.fetch("/test") { it })
            wireMockServer.verify(2, getRequestedFor(urlEqualTo("/test")))
        }

        @Test
        fun `fetchList should retry 503 on a page`() = runBlocking {
            stubFailThenSucceed({ get(urlEqualTo("/topics")) }, 503, "error-service-unavailable.json", fixture("simple-ok.json"))

            val items = client.fetchList("/topics") { body -> listOf(body) to null }

            assertEquals(listOf(fixture("simple-ok.json")), items)
            wireMockServer.verify(2, getRequestedFor(urlEqualTo("/topics")))
        }

        @Test
        fun `executeRequest GET should retry 503 by default`() = runBlocking {
            stubFailThenSucceed({ get(urlEqualTo("/config")) }, 503, "error-service-unavailable.json", fixture("simple-ok.json"))

            assertEquals(fixture("simple-ok.json"), client.executeRequest("/config", "GET"))
            wireMockServer.verify(2, getRequestedFor(urlEqualTo("/config")))
        }
    }

    @Nested
    inner class NonIdempotentRequests {

        @Test
        fun `produce POST should not retry 503`() = runBlocking {
            wireMockServer.stubFor(
                post(urlEqualTo("/records"))
                    .willReturn(aResponse().withStatus(503).withBody(fixture("error-service-unavailable.json")))
            )

            val exception = assertThrows<CCloudApiException> { client.executeRequest("/records", "POST", "{}") }

            assertEquals(503, exception.statusCode)
            wireMockServer.verify(1, postRequestedFor(urlEqualTo("/records")))
        }

        @Test
        fun `POST should retry 429 since the server rejected it outright`() = runBlocking {
            stubFailThenSucceed({ post(urlEqualTo("/records")) }, 429, "error-rate-limited.json", fixture("simple-ok.json"))

            assertEquals(fixture("simple-ok.json"), client.executeRequest("/records", "POST", "{}"))
            wireMockServer.verify(2, postRequestedFor(urlEqualTo("/records")))
        }

        @Test
        fun `connection reset should not be retried even for GET`() = runBlocking {
            wireMockServer.stubFor(
                get(urlEqualTo("/test")).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
            )
            val random = CountingRandom()
            val resetClient = clientFor(wireMockUrl, fastPolicy(random))

            val exception = assertThrows<CCloudApiException> { resetClient.fetch("/test") { it } }

            assertEquals(0, exception.statusCode)
            assertFalse(exception is CCloudConnectException)
            // No retry scheduled by our policy. (WireMock may still see 2 requests: HttpURLConnection
            // transparently resends once on a reset connection, which is JDK behavior, not ours.)
            assertEquals(0, random.draws, "expected no policy retries")
        }
    }

    @Nested
    inner class IdempotentPosts {

        @Test
        fun `consume POST marked idempotent should retry 503`() = runBlocking {
            stubFailThenSucceed({ post(urlEqualTo("/records:consume")) }, 503, "error-service-unavailable.json", fixture("simple-ok.json"))

            val response = client.executeRequest("/records:consume", "POST", "{}", idempotent = true)

            assertEquals(fixture("simple-ok.json"), response)
            wireMockServer.verify(2, postRequestedFor(urlEqualTo("/records:consume")))
        }
    }

    @Nested
    inner class RetryAfter {

        @Test
        fun `should wait at least Retry-After before retrying 429`() = runBlocking {
            stubFailThenSucceed({ get(urlEqualTo("/test")) }, 429, "error-rate-limited.json", fixture("simple-ok.json"), retryAfter = "1"
            )

            val start = TimeSource.Monotonic.markNow()
            assertEquals(fixture("simple-ok.json"), client.fetch("/test") { it })
            val elapsed = start.elapsedNow()

            assertTrue(elapsed >= 1.seconds, "expected to wait >= 1s for Retry-After, waited $elapsed")
            wireMockServer.verify(2, getRequestedFor(urlEqualTo("/test")))
        }
    }

    @Nested
    inner class NotRetried {

        @ParameterizedTest
        @ValueSource(ints = [400, 401, 403, 404, 409, 500])
        fun `client and non-transient server errors should not be retried`(status: Int) = runBlocking {
            wireMockServer.stubFor(
                get(urlEqualTo("/test"))
                    .willReturn(aResponse().withStatus(status).withBody(fixture("error-subject-not-found.json")))
            )

            val exception = assertThrows<CCloudApiException> { client.fetch("/test") { it } }

            assertEquals(status, exception.statusCode)
            wireMockServer.verify(1, getRequestedFor(urlEqualTo("/test")))
        }

        @Test
        fun `parse errors should not be retried`() = runBlocking {
            wireMockServer.stubFor(
                get(urlEqualTo("/test")).willReturn(aResponse().withStatus(200).withBody(fixture("simple-ok.json")))
            )

            assertThrows<IllegalStateException> {
                client.fetch<String>("/test") { throw IllegalStateException("bad payload") }
            }
            wireMockServer.verify(1, getRequestedFor(urlEqualTo("/test")))
        }
    }

    @Nested
    inner class Exhaustion {

        @Test
        fun `should rethrow last exception with status and retryAfter after maxAttempts`() = runBlocking {
            wireMockServer.stubFor(
                get(urlEqualTo("/test"))
                    .willReturn(
                        aResponse().withStatus(503)
                            .withHeader("Retry-After", "5")
                            .withBody(fixture("error-service-unavailable.json"))
                    )
            )
            // Clamp Retry-After to 2ms so the test stays fast; the clamped value is what the exception carries
            val clamped = clientFor(wireMockUrl, fastPolicy(maxRetryAfter = 2.milliseconds))

            val exception = assertThrows<CCloudApiException> { clamped.fetch("/test") { it } }

            assertEquals(503, exception.statusCode)
            assertEquals(2.milliseconds, exception.retryAfter)
            assertEquals("Service temporarily unavailable (HTTP 503)", exception.message)
            wireMockServer.verify(4, getRequestedFor(urlEqualTo("/test")))
        }

        @Test
        fun `should honor custom maxAttempts`() = runBlocking {
            wireMockServer.stubFor(get(urlEqualTo("/test")).willReturn(aResponse().withStatus(429)))
            val twoAttempts = clientFor(wireMockUrl, fastPolicy().copy(maxAttempts = 2))

            assertThrows<CCloudApiException> { twoAttempts.fetch("/test") { it } }

            wireMockServer.verify(2, getRequestedFor(urlEqualTo("/test")))
        }
    }

    @Nested
    inner class ConnectFailures {

        @Test
        fun `connection refused should be retried on GET`() = runBlocking {
            val random = CountingRandom()
            val refused = clientFor("http://localhost:${closedPort()}", fastPolicy(random))

            val exception = assertThrows<CCloudApiException> { refused.fetch("/test") { it } }

            assertInstanceOf(CCloudConnectException::class.java, exception)
            assertInstanceOf(ConnectException::class.java, exception.cause)
            assertEquals(3, random.draws, "expected 3 retries (4 attempts)")
        }

        @Test
        fun `connection refused should not be retried on produce POST`() = runBlocking {
            val random = CountingRandom()
            val refused = clientFor("http://localhost:${closedPort()}", fastPolicy(random))

            val exception = assertThrows<CCloudApiException> { refused.executeRequest("/records", "POST", "{}") }

            assertInstanceOf(CCloudConnectException::class.java, exception)
            assertEquals(0, random.draws, "expected no retries")
        }
    }

    @Nested
    inner class IsRetryable {

        private fun api(status: Int) = CCloudApiException("x", status)
        private val connect = CCloudConnectException("refused", ConnectException("refused"))

        @Test
        fun `429 should be retryable regardless of idempotency`() {
            assertTrue(CCloudRestClient.isRetryable(api(429), idempotent = true))
            assertTrue(CCloudRestClient.isRetryable(api(429), idempotent = false))
        }

        @ParameterizedTest
        @ValueSource(ints = [502, 503, 504])
        fun `gateway errors should be retryable only when idempotent`(status: Int) {
            assertTrue(CCloudRestClient.isRetryable(api(status), idempotent = true))
            assertFalse(CCloudRestClient.isRetryable(api(status), idempotent = false))
        }

        @Test
        fun `connect errors should be retryable only when idempotent`() {
            assertTrue(CCloudRestClient.isRetryable(connect, idempotent = true))
            assertFalse(CCloudRestClient.isRetryable(connect, idempotent = false))
        }

        @Test
        fun `status 0 transport failures such as timeouts should never be retryable`() {
            assertFalse(CCloudRestClient.isRetryable(api(0), idempotent = true))
            assertFalse(CCloudRestClient.isRetryable(api(0), idempotent = false))
        }

        @ParameterizedTest
        @ValueSource(ints = [400, 401, 403, 404, 409, 500, 501])
        fun `other statuses should never be retryable`(status: Int) {
            assertFalse(CCloudRestClient.isRetryable(api(status), idempotent = true))
        }
    }
}
