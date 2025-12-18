package io.confluent.intellijplugin.ccloud.auth

import com.intellij.util.io.HttpRequests
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URLEncoder

/**
 * HTTP client for Confluent Cloud OAuth operations. Uses IntelliJ's HttpRequests.
 * @see CCloudOAuthContext
 */
object CCloudOAuthHttpClient {

    @PublishedApi
    internal const val CONNECT_TIMEOUT_MS = 10_000 // 10 seconds

    @PublishedApi
    internal const val READ_TIMEOUT_MS = 60_000 // 1 minute

    // JSON serializer/deserializer
    @PublishedApi
    internal val json = Json {
        // Removes JSON specification restriction
        isLenient = true
    }

    /**
     * POST with form-urlencoded body (for Auth0 /oauth/token).
     * @param url The URL to post to
     * @param formData The form data to post
     * @return The response body
     */
    suspend inline fun <reified T> postForm(
        url: String,
        formData: Map<String, String>
    ): T = withContext(Dispatchers.IO) {
        val body = formData.entries.joinToString("&") { (k, v) ->
            "${encode(k)}=${encode(v)}"
        }

        val response = HttpRequests.post(url, "application/x-www-form-urlencoded")
            .connectTimeout(CONNECT_TIMEOUT_MS)
            .readTimeout(READ_TIMEOUT_MS)
            .connect { request ->
                request.write(body)
                request.inputStream.reader().readText()
            }

        json.decodeFromString<T>(response)
    }

    /**
     * POST with JSON body and optional Bearer auth.
     * @param url The URL to post to
     * @param jsonBody The JSON body to post
     * @param bearerToken The Bearer token to use for authentication
     * @return The response body and headers (needed for Set-Cookie extraction)
     */
    suspend fun postJsonWithHeaders(
        url: String,
        jsonBody: String,
        bearerToken: String? = null
    ): HttpResponseWithHeaders = withContext(Dispatchers.IO) {
        HttpRequests.post(url, "application/json")
            .connectTimeout(CONNECT_TIMEOUT_MS)
            .readTimeout(READ_TIMEOUT_MS)
            .tuner { conn ->
                bearerToken?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            }
            .connect { request ->
                request.write(jsonBody)
                val conn = request.connection as HttpURLConnection
                val body = request.inputStream.reader().readText()
                val headers = conn.headerFields.filterKeys { it != null }
                HttpResponseWithHeaders(
                    statusCode = conn.responseCode,
                    body = body,
                    headers = headers
                )
            }
    }

    /**
     * POST with JSON body and Bearer auth, returns parsed response.
     * @param url The URL to post to
     * @param jsonBody The JSON body to post
     * @param bearerToken The Bearer token to use for authentication
     * @return The response body
     */
    suspend inline fun <reified T> postJson(
        url: String,
        jsonBody: String,
        bearerToken: String? = null
    ): T = withContext(Dispatchers.IO) {
        val response = HttpRequests.post(url, "application/json")
            .connectTimeout(CONNECT_TIMEOUT_MS)
            .readTimeout(READ_TIMEOUT_MS)
            .tuner { conn ->
                bearerToken?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            }
            .connect { request ->
                request.write(jsonBody)
                request.inputStream.reader().readText()
            }

        json.decodeFromString<T>(response)
    }

    /**
     * GET with Bearer auth.
     * @param url The URL to get
     * @param bearerToken The Bearer token to use for authentication
     * @return The response body
     */
    suspend inline fun <reified T> get(
        url: String,
        bearerToken: String? = null
    ): T = withContext(Dispatchers.IO) {
        val response = HttpRequests.request(url)
            .connectTimeout(CONNECT_TIMEOUT_MS)
            .readTimeout(READ_TIMEOUT_MS)
            .tuner { conn ->
                bearerToken?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            }
            .connect { it.inputStream.reader().readText() }

        json.decodeFromString<T>(response)
    }

    @PublishedApi
    internal fun encode(s: String): String = URLEncoder.encode(s, Charsets.UTF_8)

    /**
     * Extract cookie value from Set-Cookie headers.
     * @param headers The headers to extract the cookie from
     * @param cookieName The name of the cookie to extract
     * @return The value of the cookie
     */
    fun extractCookie(headers: Map<String, List<String>>, cookieName: String): String? {
        val setCookies = headers["Set-Cookie"] ?: headers["set-cookie"] ?: return null
        for (cookie in setCookies) {
            val parts = cookie.split(";").firstOrNull()?.split("=", limit = 2)
            if (parts?.size == 2 && parts[0].trim() == cookieName) {
                return parts[1].trim()
            }
        }
        return null
    }

    data class HttpResponseWithHeaders(
        val statusCode: Int,
        val body: String,
        val headers: Map<String, List<String>>
    )
}
