package io.confluent.intellijplugin.client

import io.confluent.intellijplugin.core.util.BdIdeRegistryUtil
import io.confluent.intellijplugin.core.util.BdtUrlUtils
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Cheap reachability check run before `describeCluster`. Bounded so that a host silently dropping packets
 * (firewall, VPN down) can't block for the OS connect timeout (~75s on macOS) per URL.
 */
internal object TcpProbe {
    /** Defaults; the effective values come from the Registry (see [BdIdeRegistryUtil]) so they can be changed without a release. */
    const val CONNECT_TIMEOUT_MS = 20_000
    const val TOTAL_TIMEOUT_MS = 30_000

    fun interface Connector {
        fun connect(host: String, port: Int, timeoutMs: Int): Boolean
    }

    private val socketConnector = Connector { host, port, timeoutMs ->
        try {
            Socket().use {
                it.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: IOException) {
            false
        }
    }

    /**
     * Parses a comma-separated `bootstrap.servers` value and probes it with the default timeouts.
     * Entries are parsed lazily, so ones after the first reachable endpoint are never touched.
     */
    fun anyReachable(bootstrapServers: String): Boolean = anyReachable(lazyEndpoints(bootstrapServers))

    fun parseEndpoints(bootstrapServers: String): List<Pair<String, Int>> = lazyEndpoints(bootstrapServers).toList()

    private fun lazyEndpoints(bootstrapServers: String): Sequence<Pair<String, Int>> =
        bootstrapServers.splitToSequence(",").map {
            val urlObject = BdtUrlUtils.convertToUrlObject(it.trim())
            urlObject.host to urlObject.port
        }

    fun anyReachable(
        endpoints: List<Pair<String, Int>>,
        connectTimeoutMs: Int = BdIdeRegistryUtil.KAFKA_TCP_PROBE_CONNECT_TIMEOUT_MS,
        totalTimeoutMs: Int = BdIdeRegistryUtil.KAFKA_TCP_PROBE_TOTAL_TIMEOUT_MS,
        nanoTime: () -> Long = System::nanoTime,
        connector: Connector = socketConnector,
    ): Boolean = anyReachable(endpoints.asSequence(), connectTimeoutMs, totalTimeoutMs, nanoTime, connector)

    /**
     * Returns true as soon as one endpoint accepts a connection. Each attempt gets at most [connectTimeoutMs],
     * further clipped to what is left of [totalTimeoutMs]; once the budget is spent the remaining endpoints are skipped.
     */
    fun anyReachable(
        endpoints: Sequence<Pair<String, Int>>,
        connectTimeoutMs: Int = BdIdeRegistryUtil.KAFKA_TCP_PROBE_CONNECT_TIMEOUT_MS,
        totalTimeoutMs: Int = BdIdeRegistryUtil.KAFKA_TCP_PROBE_TOTAL_TIMEOUT_MS,
        nanoTime: () -> Long = System::nanoTime,
        connector: Connector = socketConnector,
    ): Boolean {
        val deadline = nanoTime() + TimeUnit.MILLISECONDS.toNanos(totalTimeoutMs.toLong())
        return endpoints.any { (host, port) ->
            val remainingMs = TimeUnit.NANOSECONDS.toMillis(deadline - nanoTime())
            remainingMs > 0 && connector.connect(host, port, minOf(connectTimeoutMs.toLong(), remainingMs).toInt())
        }
    }
}
