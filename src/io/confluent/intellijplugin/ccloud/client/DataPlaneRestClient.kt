package io.confluent.intellijplugin.ccloud.client

import com.intellij.util.io.HttpRequests
import io.confluent.intellijplugin.ccloud.auth.CCloudAuthService
import io.confluent.intellijplugin.ccloud.model.response.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection

/**
 * REST client for Confluent Cloud data plane APIs.
 * Uses data plane OAuth token for authentication.
 *
 * Supports:
 * - Kafka REST API v3: List/describe topics, configs (no extra headers)
 * - Schema Registry API v1: Subjects, schemas (requires "target-sr-cluster" header)
 *
 * @param baseUrl Cluster REST endpoint (e.g., https://pkc-xxx.region.cloud)
 * @param additionalHeaders Extra headers (e.g., "target-sr-cluster" for SR)
 */
class DataPlaneRestClient(
    baseUrl: String,
    private val additionalHeaders: Map<String, String> = emptyMap()
) : ControlPlaneRestClient(baseUrl) {

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000 // 10 seconds
        private const val READ_TIMEOUT_MS = 60_000    // 1 minute
    }

    /**
     * Get authentication headers with data plane OAuth token.
     * Adds any additional headers (e.g., "target-sr-cluster" for Schema Registry).
     */
    override fun getAuthHeaders(): Map<String, String> {
        val authService = CCloudAuthService.getInstance()

        if (!authService.isSignedIn()) {
            throw IllegalStateException("Not signed in to Confluent Cloud")
        }

        val token = authService.getDataPlaneToken()
            ?: throw IllegalStateException("No data plane token available")

        val baseHeaders = mapOf(
            "Authorization" to "Bearer $token",
            "Content-Type" to "application/json"
        )

        return baseHeaders + additionalHeaders
    }

    /**
     * Fetch paginated list of items.
     * Convenience method that wraps the protected listItems with auth headers.
     */
    suspend fun <T> fetchList(
        path: String,
        parser: (String) -> Pair<List<T>, String?>
    ): List<T> {
        val headers = getAuthHeaders()
        return listItems(headers, path, PageLimits.DEFAULT, parser)
    }

    /**
     * Fetch a single item (non-paginated endpoint).
     * Convenience method that wraps the protected fetchItem with auth headers.
     */
    suspend fun <T> fetch(
        path: String,
        parser: (String) -> T
    ): T {
        val headers = getAuthHeaders()
        return fetchItem(headers, path, parser)
    }

    /**
     * Execute HTTP request with any method (POST, DELETE, PATCH, PUT).
     * Returns response body as string.
     */
    private suspend fun executeRequest(
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
