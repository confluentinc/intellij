package io.confluent.intellijplugin.ccloud.client

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64

/**
 * Base HTTP client for Confluent Cloud control plane API calls.
 * Handles API key authentication (before OAuth) and JSON parsing.
 * After OAuth is implemented, this will be updated to use Bearer tokens:
 * - Change apiKey/apiSecret to getAccessToken
 * - Change "Basic $credentials" to "Bearer $token"
 */
abstract class CloudRestClient(
    private val apiKey: String,
    private val apiSecret: String,
    protected val baseUrl: String
) {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    /**
     * Get headers for API requests, including API key authentication.
     */
    protected fun headersFor(@Suppress("UNUSED_PARAMETER") connectionId: String): Map<String, String> {
        val credentials = Base64.getEncoder()
            .encodeToString("$apiKey:$apiSecret".toByteArray())
        return mapOf(
            "Authorization" to "Basic $credentials",
            "Content-Type" to "application/json"
        )
    }

    /**
     * List items from an API endpoint.
     */
    protected suspend fun <T> listItems(
        headers: Map<String, String>,
        uri: String,
        parser: (String) -> List<T>
    ): List<T> = withContext(Dispatchers.IO) {
        val url = if (uri.startsWith("http")) uri else "$baseUrl$uri"
        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))

        headers.forEach { (key, value) ->
            requestBuilder.header(key, value)
        }

        val request = requestBuilder.GET().build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() !in 200..299) {
            throw CloudApiException(
                "HTTP ${response.statusCode()}: ${response.body()?.take(200) ?: "Unknown error"}",
                response.statusCode()
            )
        }

        parser(response.body())
    }

}

/**
 * Exception thrown when API calls fail.
 */
class CloudApiException(message: String, val statusCode: Int) : Exception(message)

