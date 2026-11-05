package io.confluent.intellijplugin.ccloud.client

import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.http.Fault
import com.github.tomakehurst.wiremock.stubbing.Scenario
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
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
        @JvmField
        @RegisterExtension
        val wireMock = wireMockExtension()

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

    private lateinit var client: CCloudRestClient

    // Millisecond waits, but the production Retry-After clamp so the RetryAfter test sees a real delay
    private fun fastPolicy(random: Random = Random.Default, maxRetryAfter: Duration = 60.seconds) =
        CCloudRetryPolicy.immediate().copy(maxRetryAfter = maxRetryAfter, random = random)

    private fun clientFor(baseUrl: String, policy: CCloudRetryPolicy = fastPolicy()) =
        testClient(baseUrl, retryPolicy = policy)

    @BeforeEach
    fun setup() {
        client = clientFor(wireMock.baseUrl())
    }

    /** First call returns [status] (with optional Retry-After), subsequent calls return 200 with [okBody]. */
    private fun stubFailThenSucceed(
        request: () -> MappingBuilder,
        status: Int,
        errorFixture: String,
        retryAfter: String? = null,
        okBody: String = fixture("simple-ok.json")
    ) {
        val failure = aResponse().withStatus(status).withBody(fixture(errorFixture))
        retryAfter?.let { failure.withHeader("Retry-After", it) }
        stubFailThenSucceed(request, failure, okBody)
    }

    /** First call returns [failure], subsequent calls return 200 with [okBody]. */
    private fun stubFailThenSucceed(
        request: () -> MappingBuilder,
        failure: ResponseDefinitionBuilder,
        okBody: String = fixture("simple-ok.json")
    ) {
        wireMock.stubFor(
            request().inScenario("retry").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(failure).willSetStateTo("recovered")
        )
        wireMock.stubFor(
            request().inScenario("retry").whenScenarioStateIs("recovered")
                .willReturn(aResponse().withStatus(200).withBody(okBody))
        )
    }

    @Nested
    inner class IdempotentGets {

        @Test
        fun `fetch should retry 503 and succeed`() = runBlocking {
            stubFailThenSucceed({ get(urlEqualTo("/test")) }, 503, "error-service-unavailable.json")

            assertEquals(fixture("simple-ok.json"), client.fetch("/test") { it })
            wireMock.verify(2, getRequestedFor(urlEqualTo("/test")))
        }

        @ParameterizedTest
        @ValueSource(ints = [502, 504])
        fun `fetch should retry other gateway errors`(status: Int) = runBlocking {
            stubFailThenSucceed({ get(urlEqualTo("/test")) }, status, "error-service-unavailable.json")

            assertEquals(fixture("simple-ok.json"), client.fetch("/test") { it })
            wireMock.verify(2, getRequestedFor(urlEqualTo("/test")))
        }

        @Test
        fun `fetchList should retry 503 on a page`() = runBlocking {
            stubFailThenSucceed({ get(urlEqualTo("/topics")) }, 503, "error-service-unavailable.json")

            val items = client.fetchList("/topics") { body -> listOf(body) to null }

            assertEquals(listOf(fixture("simple-ok.json")), items)
            wireMock.verify(2, getRequestedFor(urlEqualTo("/topics")))
        }

        @Test
        fun `executeRequest GET should retry 503 by default`() = runBlocking {
            stubFailThenSucceed({ get(urlEqualTo("/config")) }, 503, "error-service-unavailable.json")

            assertEquals(fixture("simple-ok.json"), client.executeRequest("/config", "GET"))
            wireMock.verify(2, getRequestedFor(urlEqualTo("/config")))
        }

        @Test
        fun `429 with unreadable error body should still be retried`() = runBlocking {
            stubFailThenSucceed(
                { get(urlEqualTo("/test")) },
                aResponse().withStatus(429)
                    .withHeader("Content-Encoding", "gzip")
                    .withBody("definitely not gzip")
            )

            assertEquals(fixture("simple-ok.json"), client.fetch("/test") { it })
            wireMock.verify(2, getRequestedFor(urlEqualTo("/test")))
        }
    }

    @Nested
    inner class NonIdempotentRequests {

        @Test
        fun `produce POST should not retry 503`() = runBlocking {
            wireMock.stubFor(
                post(urlEqualTo("/records"))
                    .willReturn(aResponse().withStatus(503).withBody(fixture("error-service-unavailable.json")))
            )

            val exception = assertThrows<CCloudApiException> { client.executeRequest("/records", "POST", "{}") }

            assertEquals(503, exception.statusCode)
            wireMock.verify(1, postRequestedFor(urlEqualTo("/records")))
        }

        @Test
        fun `POST should retry 429 since the server rejected it outright`() = runBlocking {
            stubFailThenSucceed({ post(urlEqualTo("/records")) }, 429, "error-rate-limited.json")

            assertEquals(fixture("simple-ok.json"), client.executeRequest("/records", "POST", "{}"))
            wireMock.verify(2, postRequestedFor(urlEqualTo("/records")))
        }
    }

    @Nested
    inner class IdempotentPosts {

        @Test
        fun `read-only POST marked idempotent should retry 503`() = runBlocking {
            stubFailThenSucceed({ post(urlEqualTo("/subjects/s")) }, 503, "error-service-unavailable.json")

            val response = client.executeRequest("/subjects/s", "POST", "{}", idempotent = true)

            assertEquals(fixture("simple-ok.json"), response)
            wireMock.verify(2, postRequestedFor(urlEqualTo("/subjects/s")))
        }
    }

    /** `retry = false`: callers that retry at their own layer (consume) get exactly one attempt. */
    @Nested
    inner class RetryOptOut {

        private fun errorFixtureFor(status: Int) =
            if (status == 429) "error-rate-limited.json" else "error-service-unavailable.json"

        @ParameterizedTest
        @ValueSource(ints = [429, 503])
        fun `executeRequest with retry false should make exactly one attempt`(status: Int) = runBlocking<Unit> {
            wireMock.stubFor(
                post(urlEqualTo("/records:consume")).willReturn(
                    aResponse().withStatus(status).withHeader("Retry-After", "1").withBody(fixture(errorFixtureFor(status)))
                )
            )

            val exception = assertThrows<CCloudApiException> {
                client.executeRequest("/records:consume", "POST", "{}", idempotent = true, retry = false)
            }

            assertEquals(status, exception.statusCode)
            // Retry-After is still surfaced so the caller's own retry layer can honor it
            assertEquals(1.seconds, exception.retryAfter)
            wireMock.verify(1, postRequestedFor(urlEqualTo("/records:consume")))
        }

        @ParameterizedTest
        @ValueSource(ints = [429, 503])
        fun `fetch with retry false should make exactly one attempt`(status: Int) = runBlocking<Unit> {
            wireMock.stubFor(
                get(urlEqualTo("/records")).willReturn(
                    aResponse().withStatus(status).withBody(fixture(errorFixtureFor(status)))
                )
            )

            val exception = assertThrows<CCloudApiException> { client.fetch("/records", retry = false) { it } }

            assertEquals(status, exception.statusCode)
            wireMock.verify(1, getRequestedFor(urlEqualTo("/records")))
        }

        @Test
        fun `connection refused with retry false should not be retried`() = runBlocking<Unit> {
            val random = CountingRandom()
            val refused = clientFor("http://localhost:${closedPort()}", fastPolicy(random))

            assertThrows<CCloudConnectException> {
                refused.executeRequest("/records:consume", "POST", "{}", idempotent = true, retry = false)
            }

            assertEquals(0, random.draws, "expected no retries")
        }

        @Test
        fun `retry false should still succeed on the first attempt`() = runBlocking<Unit> {
            wireMock.stubFor(
                post(urlEqualTo("/records:consume"))
                    .willReturn(aResponse().withStatus(200).withBody(fixture("simple-ok.json")))
            )

            val response = client.executeRequest("/records:consume", "POST", "{}", retry = false)

            assertEquals(fixture("simple-ok.json"), response)
            wireMock.verify(1, postRequestedFor(urlEqualTo("/records:consume")))
        }
    }

    @Nested
    inner class RetryAfter {

        @Test
        fun `should wait at least Retry-After before retrying 429`() = runBlocking {
            stubFailThenSucceed({ get(urlEqualTo("/test")) }, 429, "error-rate-limited.json", retryAfter = "1")

            val start = TimeSource.Monotonic.markNow()
            assertEquals(fixture("simple-ok.json"), client.fetch("/test") { it })
            val elapsed = start.elapsedNow()

            assertTrue(elapsed >= 1.seconds, "expected to wait >= 1s for Retry-After, waited $elapsed")
            wireMock.verify(2, getRequestedFor(urlEqualTo("/test")))
        }
    }

    @Nested
    inner class NotRetried {

        @ParameterizedTest
        @ValueSource(ints = [400, 401, 403, 404, 409, 500])
        fun `client and non-transient server errors should not be retried`(status: Int) = runBlocking {
            wireMock.stubFor(
                get(urlEqualTo("/test"))
                    .willReturn(aResponse().withStatus(status).withBody(fixture("error-subject-not-found.json")))
            )

            val exception = assertThrows<CCloudApiException> { client.fetch("/test") { it } }

            assertEquals(status, exception.statusCode)
            wireMock.verify(1, getRequestedFor(urlEqualTo("/test")))
        }

        @Test
        fun `parse errors should not be retried`() = runBlocking {
            wireMock.stubFor(
                get(urlEqualTo("/test")).willReturn(aResponse().withStatus(200).withBody(fixture("simple-ok.json")))
            )

            assertThrows<IllegalStateException> {
                client.fetch<String>("/test") { throw IllegalStateException("bad payload") }
            }
            wireMock.verify(1, getRequestedFor(urlEqualTo("/test")))
        }

        @Test
        fun `connection reset should not be retried even for GET`() = runBlocking {
            wireMock.stubFor(
                get(urlEqualTo("/test")).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
            )
            val random = CountingRandom()
            val resetClient = clientFor(wireMock.baseUrl(), fastPolicy(random))

            val exception = assertThrows<CCloudApiException> { resetClient.fetch("/test") { it } }

            assertEquals(0, exception.statusCode)
            assertFalse(exception is CCloudConnectException)
            // No retry scheduled by our policy. (WireMock may still see 2 requests: HttpURLConnection
            // transparently resends once on a reset connection, which is JDK behavior, not ours.)
            assertEquals(0, random.draws, "expected no policy retries")
        }
    }

    @Nested
    inner class Exhaustion {

        @Test
        fun `should rethrow last exception with status and retryAfter after maxAttempts`() = runBlocking {
            wireMock.stubFor(
                get(urlEqualTo("/test"))
                    .willReturn(
                        aResponse().withStatus(503)
                            .withHeader("Retry-After", "5")
                            .withBody(fixture("error-service-unavailable.json"))
                    )
            )
            // Clamp Retry-After to 2ms so the test stays fast; the clamped value is what the exception carries
            val clamped = clientFor(wireMock.baseUrl(), fastPolicy(maxRetryAfter = 2.milliseconds))

            val exception = assertThrows<CCloudApiException> { clamped.fetch("/test") { it } }

            assertEquals(503, exception.statusCode)
            assertEquals(2.milliseconds, exception.retryAfter)
            assertEquals("Service temporarily unavailable (HTTP 503)", exception.message)
            wireMock.verify(4, getRequestedFor(urlEqualTo("/test")))
        }

        @Test
        fun `should honor custom maxAttempts`() = runBlocking {
            wireMock.stubFor(get(urlEqualTo("/test")).willReturn(aResponse().withStatus(429)))
            val twoAttempts = clientFor(wireMock.baseUrl(), fastPolicy().copy(maxAttempts = 2))

            assertThrows<CCloudApiException> { twoAttempts.fetch("/test") { it } }

            wireMock.verify(2, getRequestedFor(urlEqualTo("/test")))
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
