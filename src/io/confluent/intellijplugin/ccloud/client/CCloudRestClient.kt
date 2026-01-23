package io.confluent.intellijplugin.ccloud.client

import com.intellij.util.io.HttpRequests
import io.confluent.intellijplugin.ccloud.auth.CCloudAuthService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.HttpURLConnection

/**
 * HTTP client for Confluent Cloud REST APIs (control plane and data plane).
 *
 * @param baseUrl Cluster endpoint (e.g., https://api.confluent.cloud or https://pkc-xxx.region.cloud)
 * @param authType Authentication mode (control plane vs data plane token)
 * @param additionalHeaders Extra headers required for specific APIs (e.g., "target-sr-cluster" for Schema Registry)
 * @param authService Optional auth service (for testing purposes)
 */
class CCloudRestClient(
    private val baseUrl: String,
    private val authType: AuthType,
    private val additionalHeaders: Map<String, String> = emptyMap(),
    private val authService: CCloudAuthService? = null
) {
    enum class AuthType {
        CONTROL_PLANE,  // For environments, clusters, schema registries
        DATA_PLANE      // For topics, schemas, configs
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000 // 10 seconds
        private const val READ_TIMEOUT_MS = 60_000    // 1 minute
    }

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
            throw IllegalStateException("Not signed in to Confluent Cloud")
        }

        val token = when (authType) {
            AuthType.CONTROL_PLANE -> service.getControlPlaneToken()
            AuthType.DATA_PLANE -> service.getDataPlaneToken()
        } ?: throw IllegalStateException("No ${authType.name.lowercase().replace('_', ' ')} token available")

        val baseHeaders = mapOf(
            "Authorization" to "Bearer $token",
            "Content-Type" to "application/json"
        )

        return baseHeaders + additionalHeaders
    }

    /**
     * Fetch paginated list of items from a Confluent API endpoint.
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
     * Fetch a single item from a non-paginated endpoint.
     *
     * @param path Endpoint path (relative or absolute URL)
     * @param parser Parses response JSON
     * @return Parsed response
     */
    suspend fun <T> fetch(
        path: String,
        parser: (String) -> T
    ): T {
        return fetchItem(getAuthHeaders(), path, parser)
    }

    /**
     * Execute HTTP request with any method (POST, DELETE, PATCH, PUT).
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
        return executeRequestInternal(getAuthHeaders(), uri, method, body)
    }

    private suspend fun <T> listItems(
        headers: Map<String, String>,
        uri: String,
        limits: PageLimits = PageLimits.DEFAULT,
        parser: (String) -> Pair<List<T>, String?>
    ): List<T> = withContext(Dispatchers.IO) {
        val allItems = mutableListOf<T>()
        val firstUrl = if (uri.startsWith("http")) uri else "$baseUrl$uri"
        val state = PaginationState(initialUrl = firstUrl, limits = limits)
        val visitedUrls = mutableSetOf<String>()

        while (state.nextUrl != null) {
            val url = state.nextUrl!!

            // Prevent infinite loops from API bugs
            if (!visitedUrls.add(url)) {
                throw CCloudApiException("Pagination loop detected: duplicate URL encountered", 0)
            }

            val (statusCode, body) = try {
                HttpRequests.request(url)
                    .connectTimeout(CONNECT_TIMEOUT_MS)
                    .readTimeout(READ_TIMEOUT_MS)
                    .tuner { conn ->
                        headers.forEach { (key, value) ->
                            conn.setRequestProperty(key, value)
                        }
                    }
                    .connect { request ->
                        val conn = request.connection as HttpURLConnection
                        val statusCode = conn.responseCode
                        val responseBody = if (statusCode in 200..299) {
                            request.inputStream.bufferedReader().use { it.readText() }
                        } else {
                            conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                        }
                        statusCode to responseBody
                    }
            } catch (e: HttpRequests.HttpStatusException) {
                throw CCloudApiException("HTTP ${e.statusCode}: ${e.message ?: "Unknown error"}", e.statusCode)
            } catch (e: Exception) {
                throw CCloudApiException("Request failed: ${e.message ?: "Unknown error"}", 0)
            }

            if (statusCode !in 200..299) {
                throw CCloudApiException("HTTP $statusCode: ${body.ifEmpty { "Unknown error" }}", statusCode)
            }

            val (items, nextPageUrl) = parser(body)
            val page = state.createPage(items, nextPageUrl)
            allItems.addAll(page.items)

            if (!state.shouldContinue(page)) {
                break
            }
        }

        allItems
    }

    private suspend fun <T> fetchItem(
        headers: Map<String, String>,
        uri: String,
        parser: (String) -> T
    ): T = withContext(Dispatchers.IO) {
        val url = if (uri.startsWith("http")) uri else "$baseUrl$uri"

        HttpRequests.request(url)
            .connectTimeout(CONNECT_TIMEOUT_MS)
            .readTimeout(READ_TIMEOUT_MS)
            .tuner { connection ->
                headers.forEach { (key, value) ->
                    connection.setRequestProperty(key, value)
                }
            }
            .readString()
            .let(parser)
    }

    private suspend fun executeRequestInternal(
        headers: Map<String, String>,
        uri: String,
        method: String,
        body: String? = null
    ): String = withContext(Dispatchers.IO) {
        val url = if (uri.startsWith("http")) uri else "$baseUrl$uri"

        val (statusCode, responseBody) = try {
            HttpRequests.request(url)
                .connectTimeout(CONNECT_TIMEOUT_MS)
                .readTimeout(READ_TIMEOUT_MS)
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
                    val responseBody = if (statusCode in 200..299) {
                        request.inputStream.bufferedReader().use { it.readText() }
                    } else {
                        conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    }

                    statusCode to responseBody
                }
        } catch (e: Exception) {
            val statusCode = (e as? HttpRequests.HttpStatusException)?.statusCode ?: 0
            val message = e.message ?: "Unknown error"
            throw CCloudApiException("HTTP $statusCode: $message", statusCode)
        }

        if (statusCode !in 200..299) {
            throw CCloudApiException("HTTP $statusCode: ${responseBody.ifEmpty { "Unknown error" }}", statusCode)
        }

        responseBody
    }
}

class CCloudApiException(message: String, val statusCode: Int) : Exception(message)
