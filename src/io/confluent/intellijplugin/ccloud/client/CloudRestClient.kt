package io.confluent.intellijplugin.ccloud.client

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
        @Suppress("UNUSED_PARAMETER") headers: Map<String, String>,
        @Suppress("UNUSED_PARAMETER") uri: String,
        @Suppress("UNUSED_PARAMETER") parser: (String) -> List<T>
    ): List<T> {
        TODO("Not yet implemented")
    }

    /**
     * Get a single item from an API endpoint.
     */
    protected suspend fun <T> getItem(
        @Suppress("UNUSED_PARAMETER") headers: Map<String, String>,
        @Suppress("UNUSED_PARAMETER") uri: String,
        @Suppress("UNUSED_PARAMETER") parser: (String) -> T
    ): T {
        TODO("Not yet implemented")
    }
}

