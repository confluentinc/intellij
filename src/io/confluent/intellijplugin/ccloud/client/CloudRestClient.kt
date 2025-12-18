package io.confluent.intellijplugin.ccloud.client

import com.intellij.util.io.HttpRequests
import io.confluent.intellijplugin.ccloud.auth.CCloudAuthService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection

/**
 * HTTP client for Confluent Cloud control plane API calls.
 * Uses OAuth authentication via CCloudAuthService.
 */
abstract class CloudRestClient(
    protected val baseUrl: String
) {
    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000 // 10 seconds
        private const val READ_TIMEOUT_MS = 60_000 // 1 minute
    }

    /**
     * Get headers for API requests using OAuth Bearer token.
     */
    protected fun headersFor(): Map<String, String> {
        val authService = CCloudAuthService.getInstance()

        if (!authService.isSignedIn()) {
            throw IllegalStateException("Not signed in to Confluent Cloud")
        }

        val token = authService.getControlPlaneToken()
            ?: throw IllegalStateException("No control plane token available")

        return mapOf(
            "Authorization" to "Bearer $token",
            "Content-Type" to "application/json"
        )
    }

    /**
     * Fetch and parse a list of items from an API endpoint.
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
