package io.confluent.intellijplugin.ccloud.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.URLEncoder


/**
 * HTTP client for Confluent Cloud OAuth operations. Uses IntelliJ's HttpRequests.
 */
object CCloudOAuthHttpClient {

    // JSON serializer/deserializer
    @PublishedApi
    internal val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * POST with form-urlencoded body (for Auth0 /oauth/token).
     * Note: inline, reified and withContext are used to enable generic type deserialization
     */
    suspend inline fun <reified T> postForm(
        url: String,
        formData: Map<String, String>
    ): T = withContext(Dispatchers.IO) {
       TODO("Skeleton")
    }

    /**
     * POST with JSON body and optional Bearer auth.
     * Returns both response body and headers (needed for Set-Cookie extraction).
     */
    suspend fun postJsonWithHeaders(
        url: String,
        jsonBody: String,
        bearerToken: String? = null
    ): HttpResponseWithHeaders = withContext(Dispatchers.IO) {
        TODO("Skeleton")
    }

    /**
     * POST with JSON body and Bearer auth, returns parsed response.
     * Note: inline, reified and withContext are used to enable generic type deserialization
     */
    suspend inline fun <reified T> postJson(
        url: String,
        jsonBody: String,
        bearerToken: String? = null
    ): T = withContext(Dispatchers.IO) {
        TODO("Skeleton")
    }

    /**
     * GET with Bearer auth.
     * Note: inline, reified and withContext are used to enable generic type deserialization
     */
    suspend inline fun <reified T> get(
        url: String,
        bearerToken: String? = null
    ): T = withContext(Dispatchers.IO) {
        TODO("Skeleton")
    }

    @PublishedApi
    internal fun encode(s: String): String = URLEncoder.encode(s, Charsets.UTF_8)

    /**
     * Extract cookie value from Set-Cookie headers.
     */
    fun extractCookie(headers: Map<String, List<String>>, cookieName: String): String? {
        TODO("Skeleton")
    }

    data class HttpResponseWithHeaders(
        val statusCode: Int,
        val body: String,
        val headers: Map<String, List<String>>
    ) {
        val isSuccessful: Boolean get() = statusCode in 200..299
    }
}
