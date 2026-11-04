package io.confluent.intellijplugin.ccloud.client

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.io.HttpRequests
import io.confluent.intellijplugin.ccloud.auth.CCloudAuthService
import io.confluent.intellijplugin.ccloud.config.CloudConfig
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.VisibleForTesting
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import kotlin.time.Duration

/**
 * HTTP client for Confluent Cloud REST APIs (control plane and data plane).
 *
 * @param baseUrl Cluster endpoint (e.g., https://api.confluent.cloud or https://pkc-xxx.region.cloud)
 * @param authType Authentication mode (control plane vs data plane token)
 * @param additionalHeaders Extra headers required for specific APIs (e.g., "target-sr-cluster" for Schema Registry)
 * @param authService Optional auth service (for testing purposes)
 * @param retryPolicy Retry timing policy; also parses `Retry-After` and sets the longest one worth waiting for
 * @param connectTimeoutMs Connect timeout per request
 * @param readTimeoutMs Read timeout per request
 */
class CCloudRestClient(
    private val baseUrl: String,
    private val authType: AuthType,
    private val additionalHeaders: Map<String, String> = emptyMap(),
    private val authService: CCloudAuthService? = null,
    private val retryPolicy: CCloudRetryPolicy = CCloudRetryPolicy.DEFAULT,
    private val connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = READ_TIMEOUT_MS
) {
    companion object {
        internal const val CONNECT_TIMEOUT_MS = 10_000 // 10 seconds
        internal const val READ_TIMEOUT_MS = 60_000    // 1 minute

        /** Gateway/availability errors: the request may or may not have been applied. */
        private val TRANSIENT_SERVER_STATUSES = setOf(502, 503, 504)

        /**
         * Whether [e] is worth retrying. 429 is always safe (the server rejected the request
         * outright). 502/503/504 and connect failures are only retried for [idempotent] calls.
         *
         * Timeouts (status 0, not a connect error) are never retried, even for idempotent calls: a
         * timed-out attempt has already cost up to the read timeout (60s), so retrying would multiply
         * the wait (4 x 60s with the default policy). Callers that want to keep trying (e.g. the
         * consumer poll loop) do so at their own layer. For non-idempotent calls there is the added
         * risk that the server applied the request before the timeout.
         */
        @VisibleForTesting
        internal fun isRetryable(e: CCloudApiException, idempotent: Boolean): Boolean = when {
            e is CCloudConnectException -> idempotent
            e.statusCode == 429 -> true
            e.statusCode in TRANSIENT_SERVER_STATUSES -> idempotent
            else -> false
        }

        // Shared token bucket rate limiter across all client instances
        private val rateLimiter = TokenBucketRateLimiter(CloudConfig.API_RATE_LIMIT)
    }

    enum class AuthType {
        CONTROL_PLANE,  // For environments, clusters, schema registries
        DATA_PLANE      // For topics, schemas, configs
    }

    data class PageLimits(
        val maxPages: Int = Int.MAX_VALUE,
        val maxItems: Int = Int.MAX_VALUE
    ) {
        companion object {
            val DEFAULT = PageLimits()
        }
    }

    data class PageOfResults<T>(
        val items: List<T>,
        val hasMore: Boolean
    )

    /**
     * Pagination state tracking for Confluent API list requests.
     */
    class PaginationState(
        initialUrl: String?,
        val limits: PageLimits = PageLimits.DEFAULT
    ) {
        var nextUrl: String? = initialUrl
            private set

        private var pageCount = 0
        private var itemCount = 0

        /**
         * Create a new page of results from the given items and next page URL.
         * Applies item limits and tracks pagination progress.
         */
        fun <T> createPage(items: List<T>, nextPageUrl: String?): PageOfResults<T> {
            nextUrl = nextPageUrl?.trim()?.takeIf { it.isNotBlank() }

            val itemsRemaining = maxOf(0, limits.maxItems - itemCount)
            val limitedItems = items.take(itemsRemaining)

            itemCount += limitedItems.size
            pageCount++

            return PageOfResults(
                items = limitedItems,
                hasMore = nextUrl != null
            )
        }

        /**
         * Determine if pagination should continue based on the current page.
         */
        fun shouldContinue(page: PageOfResults<*>): Boolean {
            val pagesRemaining = maxOf(0, limits.maxPages - pageCount)
            val itemsRemaining = maxOf(0, limits.maxItems - itemCount)

            return page.hasMore && pagesRemaining > 0 && itemsRemaining > 0
        }
    }

    /**
     * Standard metadata returned by Confluent list APIs.
     * currently only "next" is used for pagination.
     */
    @Serializable
    data class ListMetadata(
        @SerialName("first") val first: String? = null,
        @SerialName("last") val last: String? = null,
        @SerialName("prev") val prev: String? = null,
        @SerialName("next") val next: String? = null,
        @SerialName("total_size") val totalSize: Int? = null
    )

    private fun getAuthHeaders(): Map<String, String> {
        val service = authService ?: CCloudAuthService.getInstance()

        if (!service.isSignedIn()) {
            throw IllegalStateException(KafkaMessagesBundle.message("error.ccloud.not.signed.in"))
        }

        val token = when (authType) {
            AuthType.CONTROL_PLANE -> service.getControlPlaneToken()
            AuthType.DATA_PLANE -> service.getDataPlaneToken()
        } ?: throw IllegalStateException(
            KafkaMessagesBundle.message("error.ccloud.no.token.available", authType.name.lowercase().replace('_', ' '))
        )

        val baseHeaders = mapOf(
            "Authorization" to "Bearer $token",
            "Content-Type" to "application/json",
            CCloudUserAgent.HEADER_NAME to CCloudUserAgent.headerValue()
        )

        return baseHeaders + additionalHeaders
    }

    /**
     * Run [operation] under the shared rate limiter, retrying per [retryPolicy].
     *
     * A rate-limit token is acquired before every attempt. Retries happen only for failures
     * [isRetryable] deems safe; the wait comes from [CCloudRetryPolicy.delayFor] and honors the
     * server's `Retry-After`. A `Retry-After` above [CCloudRetryPolicy.maxRetryAfter] stops retrying
     * right away instead of retrying sooner than the server asked. When retrying stops, the last
     * exception is rethrown unchanged (status code and the server's retryAfter intact).
     *
     * @param idempotent true when repeating the call cannot cause duplicate side effects
     * @param retry false for a single attempt (still rate limited), for callers that retry at their own layer
     */
    private suspend fun <T> withRetry(idempotent: Boolean, retry: Boolean = true, operation: suspend () -> T): T {
        val maxAttempts = if (retry) retryPolicy.maxAttempts else 1
        var retryIndex = 0
        while (true) {
            rateLimiter.acquire()
            try {
                return operation()
            } catch (e: CCloudApiException) {
                val attemptsMade = retryIndex + 1
                if (attemptsMade >= maxAttempts ||
                    !isRetryable(e, idempotent) ||
                    retryPolicy.exceedsMaxRetryAfter(e.retryAfter)
                ) {
                    throw e
                }
                val wait = retryPolicy.delayFor(retryIndex, e.retryAfter)
                thisLogger().warn(
                    "CCloud request failed (${describe(e)}). Retrying in ${wait.inWholeMilliseconds}ms " +
                        "(retry $attemptsMade/${maxAttempts - 1})"
                )
                delay(wait)
                retryIndex++
            }
        }
    }

    private fun describe(e: CCloudApiException): String = when (e) {
        is CCloudConnectException -> "connection failed"
        else -> "HTTP ${e.statusCode}" + (e.retryAfter?.let { ", Retry-After $it" } ?: "")
    }

    /**
     * Fetch a single item from a non-paginated endpoint.
     *
     * Applies rate limiting; retried as an idempotent GET (429, 502/503/504, connect failures)
     * unless [retry] is false.
     *
     * @param path Endpoint path (relative or absolute URL)
     * @param retry false to make a single attempt, for callers that retry at their own layer
     *   (e.g. the consumer poll loop)
     * @param parser Parses response JSON
     * @return Parsed response
     */
    suspend fun <T> fetch(
        path: String,
        retry: Boolean = true,
        parser: (String) -> T
    ): T {
        val response = withRetry(idempotent = true, retry = retry) {
            send(resolveUrl(path), "GET", getAuthHeaders()).requireSuccess()
        }
        // Parse outside the transport/retry path so parse errors keep their own type
        return withContext(Dispatchers.Default) { parser(response.body) }
    }

    /**
     * Fetch paginated list of items from a Confluent API endpoint.
     *
     * Applies rate limiting and idempotent-GET retries to each page independently.
     *
     * @param path Endpoint path (relative or absolute URL)
     * @param limits Pagination limits (default: fetch all)
     * @param parser Parses response JSON into (items, nextUrl)
     * @return Items from fetched pages, respecting pagination limits
     */
    suspend fun <T> fetchList(
        path: String,
        limits: PageLimits = PageLimits.DEFAULT,
        parser: (String) -> Pair<List<T>, String?>
    ): List<T> {
        return listItems(path, limits, parser)
    }

    /**
     * Execute HTTP request with any method (POST, DELETE, PATCH, PUT).
     *
     * Applies rate limiting. Unless [retry] is false, 429 is always retried; 502/503/504 and
     * connect failures are retried only when [idempotent] is true.
     *
     * @param uri Endpoint path (relative or absolute URL)
     * @param method HTTP method
     * @param body Optional request body
     * @param idempotent Whether repeating the request is safe. Defaults to true only for GET; pass
     *   true explicitly for read-only POSTs (e.g. schema lookup). DELETE, PUT and PATCH
     *   are deliberately treated as non-idempotent by default even though HTTP defines DELETE and
     *   PUT as idempotent: a retried DELETE whose first attempt was applied before a 503 would
     *   surface a misleading 404. Callers opt in with `idempotent = true`.
     * @param retry false to make a single attempt for every failure, including 429, for callers
     *   that retry at their own layer (e.g. consume, which the consumer poll loop retries). The
     *   call still waits for a rate-limit token.
     * @return Response body as string
     */
    suspend fun executeRequest(
        uri: String,
        method: String,
        body: String? = null,
        idempotent: Boolean = method.equals("GET", ignoreCase = true),
        retry: Boolean = true
    ): String {
        return withRetry(idempotent, retry) {
            send(resolveUrl(uri), method, getAuthHeaders(), body).requireSuccess()
        }.body
    }

    private suspend fun <T> listItems(
        uri: String,
        limits: PageLimits = PageLimits.DEFAULT,
        parser: (String) -> Pair<List<T>, String?>
    ): List<T> = withContext(Dispatchers.Default) {
        val allItems = mutableListOf<T>()
        val firstUrl = resolveUrl(uri)
        val state = PaginationState(initialUrl = firstUrl, limits = limits)
        val visitedUrls = mutableSetOf<String>()

        while (state.nextUrl != null) {
            val url = state.nextUrl!!

            // Prevent infinite loops from API bugs. Not a CCloudApiException: the server answered,
            // and retrying would hit the same loop.
            check(visitedUrls.add(url)) { "Pagination loop detected: duplicate URL encountered" }

            // Apply rate limiting to each pagination request
            val body = withRetry(idempotent = true) {
                send(url, "GET", getAuthHeaders()).requireSuccess()
            }.body

            val (items, nextPageUrl) = parser(body)
            val page = state.createPage(items, nextPageUrl)
            allItems.addAll(page.items)

            if (!state.shouldContinue(page)) {
                break
            }
        }

        allItems
    }

    private fun resolveUrl(uri: String): String = if (uri.startsWith("http")) uri else "$baseUrl$uri"

    /** Raw HTTP result. [retryAfter] is the parsed `Retry-After` header, if present and valid. */
    private class RawResponse(val statusCode: Int, val body: String, val retryAfter: Duration?)

    private fun RawResponse.requireSuccess(): RawResponse {
        if (statusCode !in 200..299) {
            throw CCloudApiException(extractErrorMessage(body, statusCode), statusCode, retryAfter)
        }
        return this
    }

    /**
     * Single HTTP exchange shared by every public entry point. Never throws for HTTP status codes;
     * the caller decides what a non-2xx means. Transport failures become [CCloudApiException] with
     * status 0, or [CCloudConnectException] when the request never reached the server.
     */
    private suspend fun send(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String? = null
    ): RawResponse = runInterruptible(Dispatchers.IO) {
        try {
            HttpRequests.request(url)
                .connectTimeout(connectTimeoutMs)
                .readTimeout(readTimeoutMs)
                .throwStatusCodeException(false)
                .tuner { conn ->
                    (conn as? HttpURLConnection)?.requestMethod = method
                    headers.forEach { (key, value) -> conn.setRequestProperty(key, value) }
                    if (body != null) conn.doOutput = true  // Required to write request body
                }
                .connect { request ->
                    val conn = request.connection as HttpURLConnection

                    if (body != null) {
                        conn.outputStream.bufferedWriter().use { it.write(body) }
                    }

                    val statusCode = conn.responseCode
                    if (statusCode in 200..299) {
                        RawResponse(statusCode, request.readString(), retryAfter = null)
                    } else {
                        // Read headers before the body: a body failure must not lose the status or Retry-After
                        val retryAfter = retryPolicy.parseRetryAfter(
                            conn.getHeaderField(CCloudRetryPolicy.RETRY_AFTER_HEADER)
                        )
                        RawResponse(statusCode, readErrorBody(request, statusCode), retryAfter)
                    }
                }
        } catch (e: ConnectException) {
            throw CCloudConnectException("Request failed: ${e.message ?: "Connection failed"}", e)
        } catch (e: UnknownHostException) {
            throw CCloudConnectException("Request failed: unknown host ${e.message ?: ""}".trimEnd(), e)
        } catch (e: NoRouteToHostException) {
            throw CCloudConnectException("Request failed: ${e.message ?: "No route to host"}", e)
        } catch (e: Exception) {
            // Includes timeouts (SocketTimeoutException) - those are deliberately not connect errors
            throw CCloudApiException("Request failed: ${e.message ?: "Unknown error"}", 0, cause = e)
        }
    }

    /**
     * Error body via `readError()`, which decompresses gzip (HttpRequests sends `Accept-Encoding: gzip`)
     * and honors the charset. An unreadable body (bad gzip, timeout mid-body) becomes empty so the
     * caller still sees the real status, unless the thread was interrupted by coroutine cancellation.
     *
     * On interrupt the IOException is rethrown so we don't fabricate a status-coded response for a
     * cancelled call. Note it is still caught by [send]'s catch-all and surfaces as
     * `CCloudApiException(0)`, not as a `CancellationException`; cancellation itself is observed at
     * the caller's next suspension point.
     */
    private fun readErrorBody(request: HttpRequests.Request, statusCode: Int): String = try {
        request.readError() ?: ""
    } catch (e: IOException) {
        if (Thread.currentThread().isInterrupted) throw e
        thisLogger().info("Could not read HTTP $statusCode error body: ${e.message}")
        ""
    }

    private fun extractErrorMessage(responseBody: String, statusCode: Int): String {
        if (responseBody.isEmpty()) return "HTTP $statusCode: Unknown error"

        return try {
            val json = JSONObject(responseBody)
            val errorsArrayMessage = json.optJSONArray("errors")
                ?.takeIf { it.length() > 0 }
                ?.getJSONObject(0)
                ?.optString("message")
                ?.takeIf { it.isNotEmpty() }

            val directMessage = json.optString("message")?.takeIf { it.isNotEmpty() }
            val message = errorsArrayMessage ?: directMessage

            message?.let { "$it (HTTP $statusCode)" }
                ?: "Request failed with HTTP $statusCode: ${responseBody.take(200)}"
        } catch (e: Exception) {
            "Request failed with HTTP $statusCode: ${responseBody.take(200)}"
        }
    }
}

/**
 * Failure of a Confluent Cloud REST call.
 *
 * @param statusCode HTTP status, or 0 when no HTTP response was received
 * @param retryAfter Server-requested delay from the `Retry-After` header, if any
 */
open class CCloudApiException(
    message: String,
    val statusCode: Int,
    val retryAfter: Duration? = null,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * The request could not be delivered because no connection was established (connection refused,
 * unknown host, no route). The server never saw the request, so retrying cannot duplicate side
 * effects. Timeouts are NOT reported this way: a timed-out request may have reached the server.
 */
class CCloudConnectException(message: String, cause: Throwable) :
    CCloudApiException(message, statusCode = 0, cause = cause)

/**
 * Token bucket rate limiter. Refills tokens at constant rate, suspends when empty.
 *
 * @param nanoTime Monotonic clock (injectable for tests). Wall-clock time is not used, so system
 *   clock adjustments cannot stall or burst the bucket.
 */
@VisibleForTesting
internal class TokenBucketRateLimiter(
    tokensPerSecond: Int,
    private val nanoTime: () -> Long = System::nanoTime
) {
    private val mutex = Mutex()
    private val tokensPerSecond: Double = tokensPerSecond.toDouble()
    private var tokens: Double = this.tokensPerSecond
    private var lastRefillNanos: Long = nanoTime()

    suspend fun acquire() {
        while (true) {
            val waitTimeMs = mutex.withLock {
                refillTokens()

                if (tokens >= 1.0) {
                    tokens -= 1.0
                    return  // Token acquired
                }

                // Calculate wait time for next token (at least 1ms so we never busy-spin)
                ((1.0 - tokens) / tokensPerSecond * 1000).toLong().coerceAtLeast(1L)
            }

            // Delay outside mutex to avoid blocking other coroutines
            delay(waitTimeMs)
        }
    }

    private fun refillTokens() {
        val now = nanoTime()
        val elapsedNanos = (now - lastRefillNanos).coerceAtLeast(0L)
        val tokensToAdd = (elapsedNanos / 1_000_000_000.0) * tokensPerSecond

        tokens = (tokens + tokensToAdd).coerceAtMost(tokensPerSecond)
        lastRefillNanos = now
    }

    /** Tokens currently available after refilling; for tests. */
    @VisibleForTesting
    internal suspend fun availableTokens(): Double = mutex.withLock {
        refillTokens()
        tokens
    }
}
