package io.confluent.intellijplugin.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

class TcpProbeTest {

    private class Attempt(val host: String, val port: Int, val timeoutMs: Int)

    /** Fake clock + connector: every attempt advances the clock by [costMs] and returns [reachable]. */
    private class FakeEnv(private val costMs: Long, private val reachable: (String) -> Boolean) {
        var nowNanos = 0L
        val attempts = mutableListOf<Attempt>()
        val clock: () -> Long = { nowNanos }
        val connector = TcpProbe.Connector { host, port, timeoutMs ->
            attempts += Attempt(host, port, timeoutMs)
            nowNanos += TimeUnit.MILLISECONDS.toNanos(costMs)
            reachable(host)
        }
    }

    @Nested
    inner class `budgeting` {

        @Test
        fun `should pass the per-url timeout to each attempt`() {
            val env = FakeEnv(costMs = 0) { false }

            TcpProbe.anyReachable(listOf("a" to 1), nanoTime = env.clock, connector = env.connector)

            assertEquals(TcpProbe.CONNECT_TIMEOUT_MS, env.attempts.single().timeoutMs)
        }

        @Test
        fun `should stop at the first reachable endpoint`() {
            val env = FakeEnv(costMs = 1) { it == "b" }

            val result = TcpProbe.anyReachable(
                listOf("a" to 1, "b" to 2, "c" to 3),
                nanoTime = env.clock,
                connector = env.connector,
            )

            assertTrue(result)
            assertEquals(listOf("a", "b"), env.attempts.map { it.host })
        }

        @Test
        fun `should return false when no endpoint is reachable`() {
            val env = FakeEnv(costMs = 1) { false }

            val result = TcpProbe.anyReachable(
                listOf("a" to 1, "b" to 2),
                nanoTime = env.clock,
                connector = env.connector,
            )

            assertFalse(result)
            assertEquals(2, env.attempts.size)
        }

        @Test
        fun `should return false for an empty endpoint list`() {
            val env = FakeEnv(costMs = 0) { true }

            assertFalse(TcpProbe.anyReachable(emptyList(), nanoTime = env.clock, connector = env.connector))
            assertTrue(env.attempts.isEmpty())
        }

        @Test
        fun `should clip the attempt timeout to the remaining total budget`() {
            // a 20s timeout leaves 10s of the 30s budget: second attempt only gets the remaining 10s
            val env = FakeEnv(costMs = 20_000) { false }

            TcpProbe.anyReachable(
                listOf("a" to 1, "b" to 2),
                nanoTime = env.clock,
                connector = env.connector,
            )

            assertEquals(listOf(20_000, 10_000), env.attempts.map { it.timeoutMs })
        }

        @Test
        fun `should skip remaining endpoints once the total budget is spent`() {
            val env = FakeEnv(costMs = 20_000) { false }

            val result = TcpProbe.anyReachable(
                listOf("a" to 1, "b" to 2, "c" to 3, "d" to 4),
                nanoTime = env.clock,
                connector = env.connector,
            )

            assertFalse(result)
            // 20s + 10s = 30s cap reached after two attempts
            assertEquals(listOf("a", "b"), env.attempts.map { it.host })
        }

        @Test
        fun `should respect custom timeouts`() {
            val env = FakeEnv(costMs = 0) { false }

            TcpProbe.anyReachable(
                listOf("a" to 1),
                connectTimeoutMs = 200,
                totalTimeoutMs = 1_000,
                nanoTime = env.clock,
                connector = env.connector,
            )

            assertEquals(200, env.attempts.single().timeoutMs)
        }
    }

    @Nested
    inner class `bootstrap servers parsing` {

        @Test
        fun `should split a comma separated list and trim whitespace`() {
            assertEquals(
                listOf("broker1" to 9092, "broker2" to 9093),
                TcpProbe.parseEndpoints("broker1:9092, broker2:9093")
            )
        }

        @Test
        fun `should parse a single bootstrap server`() {
            assertEquals(listOf("localhost" to 9092), TcpProbe.parseEndpoints("localhost:9092"))
        }

        @Test
        fun `should probe a bootstrap string`() {
            val closedPort = ServerSocket(0).use { it.localPort }
            ServerSocket(0).use { server ->
                assertTrue(TcpProbe.anyReachable("127.0.0.1:$closedPort,127.0.0.1:${server.localPort}"))
            }
            assertFalse(TcpProbe.anyReachable("127.0.0.1:$closedPort"))
        }

        @Test
        fun `should not parse entries after the first reachable endpoint`() {
            ServerSocket(0).use { server ->
                assertTrue(TcpProbe.anyReachable("127.0.0.1:${server.localPort},"))
            }
        }
    }

    @Nested
    inner class `with real sockets` {

        @Test
        fun `should be reachable when a server is listening`() {
            ServerSocket(0).use { server ->
                assertTrue(TcpProbe.anyReachable(listOf("127.0.0.1" to server.localPort)))
            }
        }

        @Test
        fun `should not be reachable when nothing is listening`() {
            val closedPort = ServerSocket(0).use { it.localPort }

            assertFalse(TcpProbe.anyReachable(listOf("127.0.0.1" to closedPort)))
        }

        @Test
        fun `should fall through a dead endpoint to a live one`() {
            val closedPort = ServerSocket(0).use { it.localPort }
            ServerSocket(0).use { server ->
                val endpoints = listOf("127.0.0.1" to closedPort, "127.0.0.1" to server.localPort)

                assertTrue(TcpProbe.anyReachable(endpoints))
            }
        }

        @Test
        fun `should not be reachable for an unresolvable host`() {
            assertFalse(TcpProbe.anyReachable(listOf("host.invalid" to 9092)))
        }
    }
}
