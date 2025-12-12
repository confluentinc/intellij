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
     *
     * @param connectionId Connection identifier for future OAuth implementation.
     *                     Currently unused but will be needed to retrieve OAuth tokens
     *                     from the authentication provider once OAuth login is implemented.
     *                     When OAuth is available, this will map to a token provider that
     *                     returns Bearer tokens instead of using API key/secret.
     */
    protected fun headersFor(connectionId: String): Map<String, String> {
        // TODO: Once OAuth is implemented, use connectionId to get Bearer token:
        //   val token = oAuthProvider.getAccessToken(connectionId)
        //   return mapOf("Authorization" to "Bearer $token", ...)
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

