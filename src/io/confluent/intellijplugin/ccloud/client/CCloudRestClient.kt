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
import org.json.JSONObject
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
 * @param retryPolicy Retry timing policy; also clamps parsed `Retry-After` values
 */
class CCloudRestClient(
    private val baseUrl: String,
    private val authType: AuthType,
    private val additionalHeaders: Map<String, String> = emptyMap(),
    private val authService: CCloudAuthService? = null,
    private val retryPolicy: CCloudRetryPolicy = CCloudRetryPolicy.DEFAULT
) {
    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000 // 10 seconds
        private const val READ_TIMEOUT_MS = 60_000    // 1 minute
        private const val MAX_RETRY_ATTEMPTS = 4
        private const val INITIAL_BACKOFF_MS = 1000L
        private const val MAX_BACKOFF_MS = 10_000L

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
     * Execute operation with rate limiting and retry on 429 errors.
     * Uses token bucket to throttle requests, exponential backoff as fallback.
     */
    private suspend fun <T> withRateLimit(operation: suspend () -> T): T {
        var attempt = 0
        var lastException: CCloudApiException? = null

        while (attempt < MAX_RETRY_ATTEMPTS) {
            try {
                // Acquire rate limit token before making request
                rateLimiter.acquire()
                return operation()
            } catch (e: CCloudApiException) {
                // Retry only on 429 (rate limit) errors, and only if attempts remain
                if (e.statusCode == 429 && attempt < MAX_RETRY_ATTEMPTS - 1) {
                    val delayMs = calculateExponentialBackoff(attempt)
                    thisLogger().warn(
                        "Rate limit exceeded (HTTP 429). Retrying in ${delayMs}ms (attempt ${attempt + 1}/${MAX_RETRY_ATTEMPTS - 1})"
                    )
                    delay(delayMs)
                    attempt++
                    lastException = e
                } else {
                    // Non-429 error or max retries reached - propagate exception
                    throw e
                }
            }
        }

        // Max retries exceeded - throw last exception
        throw lastException ?: CCloudApiException("Max retry attempts exceeded", 429)
    }

    /** Calculate exponential backoff: 1s -> 2s -> 4s -> 8s (max 10s). */
    private fun calculateExponentialBackoff(attempt: Int): Long {
        return (INITIAL_BACKOFF_MS * (1 shl attempt)).coerceAtMost(MAX_BACKOFF_MS)
    }

    /**
     * Fetch a single item from a non-paginated endpoint.
     *
     * Automatically applies rate limiting and retries on 429 errors.
     *
     * @param path Endpoint path (relative or absolute URL)
     * @param parser Parses response JSON
     * @return Parsed response
     */
    suspend fun <T> fetch(
        path: String,
        parser: (String) -> T
    ): T {
        val response = withRateLimit {
            send(resolveUrl(path), "GET", getAuthHeaders()).requireSuccess()
        }
        // Parse outside the transport/retry path so parse errors keep their own type
        return withContext(Dispatchers.Default) { parser(response.body) }
    }

    /**
     * Fetch paginated list of items from a Confluent API endpoint.
     *
     * Automatically applies rate limiting and retries on 429 errors for each page.
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
        return listItems(getAuthHeaders(), path, limits, parser)
    }

    /**
     * Execute HTTP request with any method (POST, DELETE, PATCH, PUT).
     *
     * Automatically applies rate limiting and retries on 429 errors.
     *
     * @param uri Endpoint path (relative or absolute URL)
     * @param method HTTP method
     * @param body Optional request body
     * @return Response body as string
     */
    suspend fun executeRequest(
        uri: String,
        method: String,
        body: String? = null
    ): String {
        return withRateLimit {
            send(resolveUrl(uri), method, getAuthHeaders(), body).requireSuccess()
        }.body
    }

    private suspend fun <T> listItems(
        headers: Map<String, String>,
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

            // Prevent infinite loops from API bugs
            if (!visitedUrls.add(url)) {
                throw CCloudApiException("Pagination loop detected: duplicate URL encountered", 0)
            }

            // Apply rate limiting to each pagination request
            val body = withRateLimit {
                send(url, "GET", headers).requireSuccess()
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
                .connectTimeout(CONNECT_TIMEOUT_MS)
                .readTimeout(READ_TIMEOUT_MS)
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
                        val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                        val retryAfter = retryPolicy.parseRetryAfter(
                            conn.getHeaderField(CCloudRetryPolicy.RETRY_AFTER_HEADER)
                        )
                        RawResponse(statusCode, errorBody, retryAfter)
                    }
                }
        } catch (e: HttpRequests.HttpStatusException) {
            throw CCloudApiException("HTTP ${e.statusCode}: ${e.message ?: "Unknown error"}", e.statusCode)
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
 */
private class TokenBucketRateLimiter(tokensPerSecond: Int) {
    private val mutex = Mutex()
    private val tokensPerSecond: Double = tokensPerSecond.toDouble()
    private var tokens: Double = this.tokensPerSecond
    private var lastRefillTime: Long = System.currentTimeMillis()

    suspend fun acquire() {
        while (true) {
            val waitTimeMs = mutex.withLock {
                refillTokens()

                if (tokens >= 1.0) {
                    tokens -= 1.0
                    return  // Token acquired
                }

                // Calculate wait time for next token
                ((1.0 - tokens) / tokensPerSecond * 1000).toLong()
            }

            // Delay outside mutex to avoid blocking other coroutines
            delay(waitTimeMs)
        }
    }

    private fun refillTokens() {
        val now = System.currentTimeMillis()
        val elapsedMs = now - lastRefillTime
        val tokensToAdd = (elapsedMs / 1000.0) * tokensPerSecond

        tokens = (tokens + tokensToAdd).coerceAtMost(tokensPerSecond)
        lastRefillTime = now
    }
}
