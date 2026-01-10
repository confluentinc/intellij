package io.confluent.intellijplugin.ccloud.client

import com.intellij.util.io.HttpRequests
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.HttpURLConnection

/**
 * HTTP client for Confluent Cloud Control Plane REST API.
 * Handles resource discovery operations (environments, clusters, schema registries).
 *
 * Uses control plane OAuth token for authentication.
 */
abstract class ControlPlaneRestClient(
    protected val baseUrl: String
) {
    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000 // 10 seconds
        private const val READ_TIMEOUT_MS = 60_000 // 1 minute
    }

    /**
     * Pagination state tracking for Confluent API list requests.
     *
     * @see <a href="https://docs.confluent.io/cloud/current/api.html#section/Pagination">Confluent API Pagination</a>
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
     * Limits for pagination requests.
     */
    data class PageLimits(
        val maxPages: Int = Int.MAX_VALUE,
        val maxItems: Int = Int.MAX_VALUE
    ) {
        companion object {
            val DEFAULT = PageLimits()
        }
    }

    /**
     * A page of results from a paginated API response.
     */
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

    /**
     * Get headers for API requests.
     * Subclasses override this to provide appropriate authentication (control plane vs data plane tokens).
     */
    protected abstract fun getAuthHeaders(): Map<String, String>

    /**
     * Fetch items from a paginated Confluent API endpoint.
     *
     * @param headers HTTP headers for authentication
     * @param uri Endpoint URI (relative or absolute)
     * @param limits Pagination limits (default: fetch all)
     * @param parser Parses response JSON into (items, nextUrl)
     * @return Items from fetched pages, respecting pagination limits
     * @see <a href="https://docs.confluent.io/cloud/current/api.html#section/Pagination">Confluent API Pagination</a>
     */
    protected open suspend fun <T> listItems(
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

    /**
     * Fetch a single item from a non-paginated endpoint.
     *
     * @param headers HTTP headers for authentication
     * @param uri Endpoint URI (relative or absolute)
     * @param parser Parses response JSON
     * @return Parsed response
     */
    protected open suspend fun <T> fetchItem(
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
}

/**
 * Exception thrown when Confluent Cloud Control Plane API calls fail.
 */
class CCloudApiException(message: String, val statusCode: Int) : Exception(message)
