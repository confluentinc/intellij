package io.confluent.intellijplugin.ccloud.auth

import com.intellij.util.io.HttpRequests
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import io.confluent.intellijplugin.ccloud.client.CCloudRetryPolicy
import io.confluent.intellijplugin.ccloud.client.CCloudUserAgent
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
     * @return The response status, body and headers (headers carry `Retry-After`)
     */
    suspend fun postFormWithHeaders(
        url: String,
        formData: Map<String, String>
    ): HttpResponseWithHeaders = post(
        url = url,
        contentType = "application/x-www-form-urlencoded",
        body = formData.entries.joinToString("&") { (k, v) -> "${encode(k)}=${encode(v)}" }
    )

    /**
     * POST with JSON body and optional Bearer auth.
     * @param url The URL to post to
     * @param jsonBody The JSON body to post
     * @param bearerToken The Bearer token to use for authentication
     * @return The response status, body and headers (needed for Set-Cookie extraction)
     */
    suspend fun postJsonWithHeaders(
        url: String,
        jsonBody: String,
        bearerToken: String? = null
    ): HttpResponseWithHeaders = post(url, "application/json", jsonBody, bearerToken)

    private suspend fun post(
        url: String,
        contentType: String,
        body: String,
        bearerToken: String? = null
    ): HttpResponseWithHeaders = withContext(Dispatchers.IO) {
        HttpRequests.post(url, contentType)
            .connectTimeout(CONNECT_TIMEOUT_MS)
            .readTimeout(READ_TIMEOUT_MS)
            .throwStatusCodeException(false)
            .tuner { conn ->
                conn.setRequestProperty(CCloudUserAgent.HEADER_NAME, CCloudUserAgent.headerValue())
                bearerToken?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            }
            .connect { request ->
                request.write(body)
                val conn = request.connection as HttpURLConnection
                val responseBody = readResponseBody(request)
                HttpResponseWithHeaders(
                    statusCode = conn.responseCode,
                    body = responseBody,
                    headers = conn.headerFields.filterKeys { it != null }
                )
            }
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
            .throwStatusCodeException(false)
            .tuner { conn ->
                conn.setRequestProperty(CCloudUserAgent.HEADER_NAME, CCloudUserAgent.headerValue())
                bearerToken?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            }
            .connect { readResponseBody(it) }

        json.decodeFromString<T>(response)
    }

    private fun encode(s: String): String = URLEncoder.encode(s, Charsets.UTF_8)

    /**
     * Read response body from the appropriate stream based on status code.
     * @param request The request to read the response body from
     * @return The response body as a string
     */
    @PublishedApi
    internal fun readResponseBody(request: HttpRequests.Request): String {
        val conn = request.connection as HttpURLConnection
        val statusCode = conn.responseCode

        if (statusCode >= 500) {
            val errorBody = conn.errorStream?.reader()?.readText()
            val message = if (errorBody.isNullOrBlank()) "Server error" else "Server error: $errorBody"
            throw HttpRequests.HttpStatusException(message, statusCode, conn.url.toString())
        }

        return if (statusCode >= 400) {
            conn.errorStream?.reader()?.readText() ?: ""
        } else {
            request.inputStream.reader().readText()
        }
    }


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
    ) {
        val isSuccessful: Boolean get() = statusCode in 200..299

        /** First value of header [name], matched case-insensitively. */
        fun header(name: String): String? =
            headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()

        /** Raw `Retry-After` header value, if the server sent one. */
        val retryAfterHeader: String? get() = header(CCloudRetryPolicy.RETRY_AFTER_HEADER)
    }
}
