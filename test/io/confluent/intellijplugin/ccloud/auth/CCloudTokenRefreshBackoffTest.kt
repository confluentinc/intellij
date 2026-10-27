package io.confluent.intellijplugin.ccloud.auth

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.stubbing.Scenario
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.ccloud.client.CCloudRetryPolicy
import io.confluent.intellijplugin.ccloud.util.ResourceLoader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Drives [CCloudTokenRefreshBean] against a real [CCloudOAuthContext] and WireMock token endpoints.
 * Waits go through an injected recorder, so nothing actually sleeps.
 */
@TestApplication
class CCloudTokenRefreshBackoffTest {

    companion object {
        private lateinit var wireMockServer: WireMockServer
        private const val FIXTURES = "fixtures/ccloud-auth"
        private val TEST_TIMEOUT = 20.seconds

        /** Production waits before jitter: 5s * 1.6^n for n = 0..8 (all below the 5 min cap). */
        private val PRODUCTION_BASES = listOf(5_000.0, 8_000.0, 12_800.0, 20_480.0, 32_768.0, 52_428.8, 83_886.08, 134_217.728, 214_748.3648)
            .map { it.milliseconds }

        private fun assertDurationsClose(expected: List<Duration>, actual: List<Duration>) {
            assertEquals(expected.size, actual.size, "number of waits: $actual")
            expected.zip(actual).forEachIndexed { i, (e, a) ->
                assertTrue((e - a).absoluteValue < 1.milliseconds, "wait $i: expected $e, was $a")
            }
        }

        /** The production policy without jitter, so delays are exact. */
        private val NO_JITTER_POLICY = CCloudOAuthConfig.TOKEN_REFRESH_RETRY_POLICY.copy(jitter = 0.0)

        @JvmStatic
        @BeforeAll
        fun setUpAll() {
            wireMockServer = WireMockServer(wireMockConfig().dynamicPort())
            wireMockServer.start()

            val baseUrl = "http://localhost:${wireMockServer.port()}"
            System.setProperty("ccloud.oauth.token-uri", "$baseUrl/oauth/token")
            System.setProperty("ccloud.control-plane.token-exchange-uri", "$baseUrl/api/sessions")
            System.setProperty("ccloud.data-plane.token-exchange-uri", "$baseUrl/api/access_tokens")
            // Tokens expire before the next check, so every tick attempts a refresh
            System.setProperty("ccloud.control-plane.token-lifetime-seconds", "1")
        }

        @JvmStatic
        @AfterAll
        fun tearDownAll() {
            wireMockServer.stop()
            System.clearProperty("ccloud.oauth.token-uri")
            System.clearProperty("ccloud.control-plane.token-exchange-uri")
            System.clearProperty("ccloud.data-plane.token-exchange-uri")
            System.clearProperty("ccloud.control-plane.token-lifetime-seconds")
        }
    }

    /** Records each requested wait and returns at once; parks forever after [stopAfter] waits. */
    private class RecordingSleeper(private val stopAfter: Int = Int.MAX_VALUE) {
        val delays = CopyOnWriteArrayList<Duration>()
        val reachedLimit = CompletableDeferred<Unit>()

        suspend fun sleep(duration: Duration) {
            delays += duration
            if (delays.size >= stopAfter) {
                reachedLimit.complete(Unit)
                awaitCancellation()
            }
        }
    }

    private lateinit var parentDisposable: Disposable
    private lateinit var context: CCloudOAuthContext
    private val terminalReasons = CopyOnWriteArrayList<SignOutReason>()
    private val terminal = CompletableDeferred<SignOutReason>()

    @BeforeEach
    fun setUp() {
        parentDisposable = Disposer.newDisposable("test")
        stubSuccessfulEndpoints()
        context = CCloudOAuthContext()
        val signIn = runBlocking { context.createTokensFromAuthorizationCode("code") }
        assertTrue(signIn.isSuccess, "sign-in should succeed: ${signIn.exceptionOrNull()}")
        wireMockServer.resetRequests()
    }

    @AfterEach
    fun tearDown() {
        Disposer.dispose(parentDisposable)
        wireMockServer.resetAll()
    }

    private fun fixture(name: String) = ResourceLoader.loadResource("$FIXTURES/$name")

    private fun stubSuccessfulEndpoints() {
        wireMockServer.stubFor(
            WireMock.post("/oauth/token").willReturn(
                WireMock.aResponse().withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(fixture("oauth-token-success.json"))
            )
        )
        wireMockServer.stubFor(
            WireMock.post("/api/sessions").willReturn(
                WireMock.aResponse().withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withHeader("Set-Cookie", "auth_token=test_cp_token; Path=/; HttpOnly")
                    .withBody(fixture("control-plane-session-success.json"))
            )
        )
        wireMockServer.stubFor(
            WireMock.post("/api/access_tokens").willReturn(
                WireMock.aResponse().withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(fixture("data-plane-token-success.json"))
            )
        )
    }

    private fun stubFailure(path: String, status: Int, fixture: String? = null, retryAfter: String? = null) {
        val response = WireMock.aResponse().withStatus(status).withHeader("Content-Type", "application/json")
        fixture?.let { response.withBody(fixture(it)) }
        retryAfter?.let { response.withHeader("Retry-After", it) }
        wireMockServer.stubFor(WireMock.post(path).willReturn(response).atPriority(1))
    }

    private fun startBean(sleeper: RecordingSleeper, policy: CCloudRetryPolicy = NO_JITTER_POLICY) =
        CCloudTokenRefreshBean(
            context = context,
            parentDisposable = parentDisposable,
            onTerminal = { reason ->
                terminalReasons += reason
                terminal.complete(reason)
            },
            retryPolicy = policy,
            sleep = sleeper::sleep,
        ).also { it.start() }

    private fun refreshRequests() = wireMockServer.countRequestsMatching(
        postRequestedFor(urlEqualTo("/oauth/token")).build()
    ).count

    private fun awaitTerminal(): SignOutReason = runBlocking { withTimeout(TEST_TIMEOUT) { terminal.await() } }

    private fun awaitWaits(sleeper: RecordingSleeper) = runBlocking {
        withTimeout(TEST_TIMEOUT) { sleeper.reachedLimit.await() }
    }

    @Nested
    @DisplayName("attempt cap")
    inner class AttemptCap {

        @Test
        fun `tries exactly 10 times on persistent 5xx then signs out`() {
            stubFailure("/oauth/token", 503)
            val sleeper = RecordingSleeper()
            val bean = startBean(sleeper)

            assertEquals(SignOutReason.REFRESH_FAILED, awaitTerminal())

            assertEquals(10, refreshRequests())
            assertEquals(10, context.getFailedTokenRefreshAttempts())
            assertEquals(listOf(SignOutReason.REFRESH_FAILED), terminalReasons)
            runBlocking { withTimeout(TEST_TIMEOUT) { while (bean.isRunning()) delay(10) } }
            assertFalse(bean.isRunning())
        }
    }

    @Nested
    @DisplayName("backoff")
    inner class Backoff {

        @Test
        fun `waits grow 1_6x from 5s and total about 10 minutes before sign-out`() {
            stubFailure("/oauth/token", 503)
            val sleeper = RecordingSleeper()
            startBean(sleeper)

            awaitTerminal()

            // First entry is the idle tick before the first try; then one wait between each of the 10 tries
            assertEquals(CCloudOAuthConfig.CHECK_TOKEN_EXPIRATION_INTERVAL, sleeper.delays.first())
            assertDurationsClose(PRODUCTION_BASES, sleeper.delays.drop(1))
            val total = sleeper.delays.drop(1).fold(Duration.ZERO, Duration::plus)
            assertTrue(total > 9.minutes && total < 10.minutes, "total backoff was $total")
        }

        @Test
        fun `computed waits stop growing at maxBackoff`() {
            stubFailure("/oauth/token", 503)
            val sleeper = RecordingSleeper()
            startBean(sleeper, NO_JITTER_POLICY.copy(maxBackoff = 30.seconds))

            awaitTerminal()

            val expected = PRODUCTION_BASES.map { minOf(it, 30.seconds) }
            assertDurationsClose(expected, sleeper.delays.drop(1))
        }

        @Test
        fun `jitter only adds up to 20 percent on top of each wait`() {
            stubFailure("/oauth/token", 503)
            val sleeper = RecordingSleeper()
            startBean(sleeper, CCloudOAuthConfig.TOKEN_REFRESH_RETRY_POLICY.copy(random = Random(42)))

            awaitTerminal()

            val bases = PRODUCTION_BASES
            val backoffs = sleeper.delays.drop(1)
            assertEquals(bases.size, backoffs.size)
            bases.zip(backoffs).forEach { (base, actual) ->
                assertTrue(actual >= base && actual < base * 1.2, "$actual should be in [$base, ${base * 1.2})")
            }
            assertTrue(backoffs.all { it < 6.minutes })
        }

        @Test
        fun `returns to the idle tick after a successful retry`() {
            wireMockServer.stubFor(
                WireMock.post("/oauth/token").inScenario("recovers")
                    .whenScenarioStateIs(Scenario.STARTED)
                    .willReturn(WireMock.aResponse().withStatus(503))
                    .willSetStateTo("recovered")
                    .atPriority(1)
            )
            val sleeper = RecordingSleeper(stopAfter = 3)
            // Distinct initial backoff so it can't be confused with the 5s tick
            startBean(sleeper, NO_JITTER_POLICY.copy(initialBackoff = 7.seconds))

            awaitWaits(sleeper)

            val tick = CCloudOAuthConfig.CHECK_TOKEN_EXPIRATION_INTERVAL
            assertEquals(listOf(tick, 7.seconds, tick), sleeper.delays)
            assertEquals(0, context.getFailedTokenRefreshAttempts())
        }
    }

    @Nested
    @DisplayName("Retry-After")
    inner class RetryAfter {

        @Test
        fun `429 Retry-After is the floor for the next wait`() {
            stubFailure("/oauth/token", 429, "oauth-token-rate-limited.json", retryAfter = "120")
            val sleeper = RecordingSleeper(stopAfter = 2)
            startBean(sleeper)

            awaitWaits(sleeper)

            // Computed backoff would be 5s; Retry-After raises it
            assertEquals(120.seconds, sleeper.delays[1])
            assertTrue(terminalReasons.isEmpty())
        }

        @Test
        fun `Retry-After shorter than the backoff does not shorten it`() {
            stubFailure("/api/access_tokens", 429, retryAfter = "1")
            val sleeper = RecordingSleeper(stopAfter = 2)
            startBean(sleeper)

            awaitWaits(sleeper)

            assertEquals(5.seconds, sleeper.delays[1])
        }

        @Test
        fun `Retry-After is clamped to 5 minutes`() {
            stubFailure("/oauth/token", 429, "oauth-token-rate-limited.json", retryAfter = "3600")
            val sleeper = RecordingSleeper(stopAfter = 2)
            startBean(sleeper)

            awaitWaits(sleeper)

            assertEquals(5.minutes, sleeper.delays[1])
        }
    }

    @Nested
    @DisplayName("terminal errors")
    inner class TerminalErrors {

        @Test
        fun `invalid refresh token signs out after one try without waiting`() {
            stubFailure("/oauth/token", 403, "oauth-token-invalid-refresh-token.json")
            val sleeper = RecordingSleeper()
            startBean(sleeper)

            assertEquals(SignOutReason.REFRESH_FAILED, awaitTerminal())

            assertEquals(1, refreshRequests())
            assertEquals(listOf(CCloudOAuthConfig.CHECK_TOKEN_EXPIRATION_INTERVAL), sleeper.delays)
        }

        @Test
        fun `401 from control plane signs out after one try`() {
            stubFailure("/api/sessions", 401, "control-plane-unauthorized.json")
            val sleeper = RecordingSleeper()
            startBean(sleeper)

            assertEquals(SignOutReason.REFRESH_FAILED, awaitTerminal())

            assertEquals(1, refreshRequests())
            assertEquals(1, sleeper.delays.size)
        }

        @Test
        fun `403 from data plane signs out after one try`() {
            stubFailure("/api/access_tokens", 403, "data-plane-forbidden.json")
            val sleeper = RecordingSleeper()
            startBean(sleeper)

            assertEquals(SignOutReason.REFRESH_FAILED, awaitTerminal())

            assertEquals(1, refreshRequests())
        }
    }

    @Nested
    @DisplayName("new sign-in")
    inner class NewSignIn {

        @Test
        fun `failure counter and backoff start from zero after signing in again`() {
            stubFailure("/oauth/token", 503)
            runBlocking { repeat(3) { context.refresh() } }
            assertEquals(3, context.getFailedTokenRefreshAttempts())

            // Sign in again with working endpoints
            wireMockServer.resetAll()
            stubSuccessfulEndpoints()
            val signIn = runBlocking { context.createTokensFromAuthorizationCode("code") }
            assertTrue(signIn.isSuccess)
            assertEquals(0, context.getFailedTokenRefreshAttempts())
            assertFalse(context.hasNonTransientError())

            // The next failure waits the first backoff, not the fourth
            stubFailure("/oauth/token", 503)
            val sleeper = RecordingSleeper(stopAfter = 2)
            startBean(sleeper)
            awaitWaits(sleeper)

            assertEquals(5.seconds, sleeper.delays[1])
            assertEquals(1, context.getFailedTokenRefreshAttempts())
        }

        @Test
        fun `a fresh context starts with no failures`() {
            val fresh = CCloudOAuthContext()

            assertEquals(0, fresh.getFailedTokenRefreshAttempts())
            assertFalse(fresh.hasNonTransientError())
        }
    }
}
