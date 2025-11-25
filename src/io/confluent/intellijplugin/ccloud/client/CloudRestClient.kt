package io.confluent.intellijplugin.ccloud.client

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.confluent.intellijplugin.ccloud.auth.CloudOAuthProvider
import io.confluent.intellijplugin.ccloud.exception.CloudResourceFetchingException
import io.confluent.intellijplugin.ccloud.model.PageLimits
import io.confluent.intellijplugin.ccloud.model.response.ListMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Base HTTP client for Confluent Cloud control plane API calls.
 * Handles OAuth authentication, pagination, and JSON parsing.
 */
abstract class CloudRestClient(
    private val oauthProvider: CloudOAuthProvider,
    protected val baseUrl: String
) {
    protected val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    protected val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    /**
     * Get headers for a connection, including OAuth authorization.
     */
    protected fun headersFor(connectionId: String): Map<String, String> {
        return oauthProvider.getHeaders(connectionId)
    }

    /**
     * List items from a paginated API endpoint.
     */
    protected suspend fun <T> listItems(
        headers: Map<String, String>,
        uri: String,
        limits: PageLimits?,
        parser: (String, PaginationState) -> PageOfResults<T>
    ): List<T> = withContext(Dispatchers.IO) {
        val results = mutableListOf<T>()
        var nextUrl: String? = uri
        var pageCount = 0
        val maxPages = limits?.maxPages ?: PageLimits.DEFAULT.maxPages
        val pageSize = limits?.pageSize ?: PageLimits.DEFAULT.pageSize

        while (nextUrl != null && pageCount < maxPages) {
            val response = makeRequest(nextUrl, headers)
            val paginationState = PaginationState()
            val pageResults = parser(response, paginationState)
            
            results.addAll(pageResults.items)
            
            // Check if there's a next page
            val metadata = pageResults.metadata
            nextUrl = metadata?.next?.takeIf { it.isNotBlank() }
            pageCount++
            
            // Stop if we've reached the page size limit
            if (results.size >= pageSize) {
                break
            }
        }

        results.take(pageSize)
    }

    /**
     * Get a single item from an API endpoint.
     */
    protected suspend fun <T> getItem(
        headers: Map<String, String>,
        uri: String,
        parser: (String) -> T
    ): T = withContext(Dispatchers.IO) {
        val response = makeRequest(uri, headers)
        parser(response)
    }

    /**
     * Make an HTTP GET request.
     */
    private suspend fun makeRequest(uri: String, headers: Map<String, String>): String {
        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(uri))
            .timeout(Duration.ofSeconds(30))
            .GET()

        headers.forEach { (key, value) ->
            requestBuilder.header(key, value)
        }

        val request = requestBuilder.build()
        
        return try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() in 200..299) {
                response.body()
            } else {
                throw CloudResourceFetchingException(
                    "HTTP ${response.statusCode()} error: ${response.body()}"
                )
            }
        } catch (e: Exception) {
            if (e is CloudResourceFetchingException) throw e
            throw CloudResourceFetchingException("Failed to fetch resource from $uri", e)
        }
    }

    /**
     * Parse a raw item response.
     */
    protected fun <T> parseRawItem(
        url: String,
        json: String,
        responseClass: Class<T>
    ): T {
        val adapter = moshi.adapter(responseClass)
        return adapter.fromJson(json)
            ?: throw CloudResourceFetchingException("Failed to parse response from $url: $json")
    }

    /**
     * Pagination state (currently unused; reserved for future use).
     */
    protected class PaginationState {
        var nextUrl: String? = null
        var hasMore: Boolean = false
    }

    /**
     * Page of results with metadata.
     */
    protected data class PageOfResults<T>(
        val items: List<T>,
        val metadata: ListMetadata?
    )
}

