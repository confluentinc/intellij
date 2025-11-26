package io.confluent.intellijplugin.ccloud.client

/**
 * Base HTTP client for Confluent Cloud control plane API calls.
 * Handles OAuth authentication and JSON parsing.
 */
abstract class CloudRestClient(
    private val getHeaders: (String) -> Map<String, String>,
    protected val baseUrl: String
) {
    /**
     * Get headers for a connection, including OAuth authorization.
     */
    protected fun headersFor(connectionId: String): Map<String, String> {
        return getHeaders(connectionId)
    }

    /**
     * List items from an API endpoint.
     */
    protected suspend fun <T> listItems(
        headers: Map<String, String>,
        uri: String,
        parser: (String) -> List<T>
    ): List<T> {
        TODO("Not yet implemented")
    }

    /**
     * Get a single item from an API endpoint.
     */
    protected suspend fun <T> getItem(
        headers: Map<String, String>,
        uri: String,
        parser: (String) -> T
    ): T {
        TODO("Not yet implemented")
    }

    /**
     * Parse a raw item response.
     */
    protected fun <T> parseRawItem(
        url: String,
        json: String,
        responseClass: Class<T>
    ): T {
        TODO("Not yet implemented")
    }
}

