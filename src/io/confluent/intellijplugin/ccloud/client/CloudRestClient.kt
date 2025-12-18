package io.confluent.intellijplugin.ccloud.client

import com.intellij.util.io.HttpRequests
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.util.Base64

/**
 * HTTP client for Confluent Cloud control plane API calls.
 * Handles API key authentication (OAuth support planned).
 */
abstract class CloudRestClient(
    private val apiKey: String,
    private val apiSecret: String,
    protected val baseUrl: String
) {
    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000 // 10 seconds
        private const val READ_TIMEOUT_MS = 60_000 // 1 minute
    }

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
     * Fetch and parse a list of items from an API endpoint.
     *
     * @param T The type of items to be parsed from the response.
     * @param headers HTTP headers to include in the request (e.g., Authorization, Content-Type).
     * @param uri The endpoint URI. If it starts with "http", it is used as a full URL.
     *            Otherwise, it is treated as a relative path and prepended with [baseUrl].
     *            For example: "/org/v2/environments" becomes "https://api.confluent.cloud/org/v2/environments".
     * @param parser Function to parse the JSON response body into a list of items of type [T].
     * @return List of parsed items from the API response.
     * @throws CloudApiException if the HTTP response status code is not in the 2xx range.
     */
    protected suspend fun <T> listItems(
        headers: Map<String, String>,
        uri: String,
        parser: (String) -> List<T>
    ): List<T> = withContext(Dispatchers.IO) {
        val url = if (uri.startsWith("http")) uri else "$baseUrl$uri"

        val (statusCode, body) = HttpRequests.request(url)
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

        if (statusCode !in 200..299) {
            throw CloudApiException("HTTP $statusCode: ${body.ifEmpty { "Unknown error" }}", statusCode)
        }

        parser(body)
    }

}

/**
 * Exception thrown when API calls fail.
 */
class CloudApiException(message: String, val statusCode: Int) : Exception(message)

