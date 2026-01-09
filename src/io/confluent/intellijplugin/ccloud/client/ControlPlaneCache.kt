package io.confluent.intellijplugin.ccloud.client

import com.intellij.openapi.project.Project
import io.confluent.intellijplugin.ccloud.config.CloudConfig
import io.confluent.intellijplugin.ccloud.fetcher.CloudFetcherImpl
import io.confluent.intellijplugin.ccloud.model.Environment
import io.confluent.intellijplugin.ccloud.model.Cluster
import io.confluent.intellijplugin.ccloud.model.SchemaRegistry
import io.confluent.intellijplugin.core.monitoring.connection.MonitoringClient
import io.confluent.intellijplugin.rfs.ConfluentConnectionData
import kotlinx.coroutines.runBlocking

/**
 * Control plane cache for Confluent Cloud organizational resources.
 * Wraps [CloudFetcherImpl] and provides in-memory caching for environments, clusters, and schema registries.
 *
 * ## Architecture
 *
 * This cache sits between the driver layer and the control plane REST API:
 * - **ControlPlaneRestClient** - Low-level HTTP/REST mechanics
 * - **CloudFetcherImpl** - Fetches resources from control plane API
 * - **ControlPlaneCache** - Caches organizational resources (this class)
 *
 * Future: **DataPlaneCache** will handle cluster-level resources (topics, consumer groups, ACLs)
 * using native Kafka protocol and Schema Registry REST API.
 *
 * ## Caching Strategy
 *
 * In-memory caching reduces API calls and improves UI responsiveness:
 *
 * - **Environments**: Cached as a single list. Populated during [checkConnectionInner] on initial connection.
 * - **Kafka Clusters**: Cached per environment ID in a map. Populated lazily on first access via [getKafkaClusters].
 * - **Schema Registry**: Cached per environment ID in a map. Populated lazily on first access via [getSchemaRegistry].
 *
 * ## Cache Invalidation
 *
 * The cache does not auto-expire. Invalidation is manual via:
 *
 * - **[refreshEnvironments]**: Fetches fresh environments and replaces the cached list.
 * - **[refreshKafkaClusters]**: Fetches fresh clusters for a specific environment and updates the cache entry.
 * - **[refreshSchemaRegistry]**: Fetches fresh schema registry for a specific environment and updates the cache entry.
 * - **[clearCache]**: Clears all cached data (environments, clusters, and schema registry).
 * - **[dispose]**: Called on disposal; clears all caches and releases the fetcher.
 *
 * Callers should invoke the appropriate refresh method when they expect data to have changed
 * (e.g., after user creates a new cluster or clicks a refresh button).
 *
 * Note: A refresh button for the Confluent toolwindow is planned but not yet implemented.
 */
class ControlPlaneCache(
    project: Project?,
    private val connectionData: ConfluentConnectionData
) : MonitoringClient(project) {

    private var fetcher: CloudFetcherImpl? = null

    // Cached data
    private var cachedEnvironments: List<Environment>? = null
    private val cachedClusters = mutableMapOf<String, List<Cluster>>()
    private val cachedSchemaRegistry = mutableMapOf<String, List<SchemaRegistry>>()

    override fun getRealUri(): String = CloudConfig.CONTROL_PLANE_BASE_URL

    override fun connectInner(calledByUser: Boolean) {
        // OAuth authentication is handled by CCloudAuthService
        fetcher = CloudFetcherImpl()
    }

    override fun checkConnectionInner() {
        val f = fetcher ?: throw IllegalStateException("Cache not initialized")
        // Validate connection by fetching environments
        cachedEnvironments = runBlocking {
            f.getEnvironments()
        }
    }

    fun getEnvironments(): List<Environment> = cachedEnvironments ?: emptyList()

    /**
     * Get Kafka clusters for an environment. Uses `runBlocking` for lazy fetching.
     *
     * Note: While `runBlocking` is generally discouraged, it's acceptable here because:
     * 1. These methods are called from `ConfluentDriver.doLoadChildren()` which already
     *    runs on a background thread (handled by the RFS framework)
     * 2. Results are cached, so blocking only occurs on first access per environment
     * 3. The lazy-loading pattern ensures we don't fetch data until it's actually needed
     */
    fun getKafkaClusters(environmentId: String): List<Cluster> = cachedClusters.getOrPut(environmentId) {
        fetcher?.let { f ->
            runBlocking { f.getKafkaClusters(environmentId) }
        } ?: emptyList()
    }

    /**
     * Get Schema Registries for an environment. See [getKafkaClusters] for notes on `runBlocking` usage.
     */
    fun getSchemaRegistry(environmentId: String): List<SchemaRegistry> = cachedSchemaRegistry.getOrPut(environmentId) {
        fetcher?.let { f ->
            runBlocking { f.getSchemaRegistry(environmentId) }
        } ?: emptyList()
    }

    fun refreshEnvironments(): List<Environment> {
        cachedEnvironments = fetcher?.let { f ->
            runBlocking { f.getEnvironments() }
        } ?: emptyList()
        return cachedEnvironments ?: emptyList()
    }

    fun refreshKafkaClusters(environmentId: String): List<Cluster> {
        val clusters = fetcher?.let { f ->
            runBlocking { f.getKafkaClusters(environmentId) }
        } ?: emptyList()
        cachedClusters[environmentId] = clusters
        return clusters
    }

    fun refreshSchemaRegistry(environmentId: String): List<SchemaRegistry> {
        val registries = fetcher?.let { f ->
            runBlocking { f.getSchemaRegistry(environmentId) }
        } ?: emptyList()
        cachedSchemaRegistry[environmentId] = registries
        return registries
    }

    fun clearCache() {
        cachedEnvironments = null
        cachedClusters.clear()
        cachedSchemaRegistry.clear()
    }

    override fun dispose() {
        fetcher = null
        clearCache()
    }
}

