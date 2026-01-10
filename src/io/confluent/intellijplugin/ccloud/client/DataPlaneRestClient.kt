package io.confluent.intellijplugin.ccloud.client

import io.confluent.intellijplugin.ccloud.auth.CCloudAuthService

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
}
