package io.confluent.intellijplugin.client

import com.intellij.openapi.project.Project
import io.confluent.intellijplugin.ccloud.config.CloudConfig
import io.confluent.intellijplugin.ccloud.fetcher.CloudFetcherImpl
import io.confluent.intellijplugin.ccloud.model.CCloudEnvironment
import io.confluent.intellijplugin.ccloud.model.KafkaCluster
import io.confluent.intellijplugin.ccloud.model.SchemaRegistry
import io.confluent.intellijplugin.core.monitoring.connection.MonitoringClient
import io.confluent.intellijplugin.rfs.ConfluentConnectionData
import kotlinx.coroutines.runBlocking

/**
 * Client for Confluent Cloud API operations.
 * Wraps [CloudFetcherImpl] and provides caching for environments, clusters, and schema registries.
 *
 * ## Caching Strategy
 *
 * This client uses in-memory caching to reduce API calls and improve UI responsiveness:
 *
 * - **Environments**: Cached as a single list. Populated during [checkConnectionInner] on initial connection.
 * - **Kafka Clusters**: Cached per environment ID in a map. Populated lazily on first access via [getKafkaClusters].
 * - **Schema Registries**: Cached per environment ID in a map. Populated lazily on first access via [getSchemaRegistries].
 *
 * ## Cache Invalidation
 *
 * The cache does not auto-expire. Invalidation is manual via:
 *
 * - **[refreshEnvironments]**: Fetches fresh environments and replaces the cached list.
 * - **[refreshClusters]**: Fetches fresh clusters for a specific environment and updates the cache entry.
 * - **[refreshSchemaRegistries]**: Fetches fresh schema registries for a specific environment and updates the cache entry.
 * - **[clearCache]**: Clears all cached data (environments, clusters, and schema registries).
 * - **[dispose]**: Called on client disposal; clears all caches and releases the fetcher.
 *
 * Callers should invoke the appropriate refresh method when they expect data to have changed
 * (e.g., after user creates a new cluster or clicks a refresh button).
 *
 * Note: A refresh button for the Confluent toolwindow is planned but not yet implemented.
 */
class ConfluentClient(
    project: Project?,
    private val connectionData: ConfluentConnectionData
) : MonitoringClient(project) {

    private var fetcher: CloudFetcherImpl? = null

    // Cached data
    private var cachedEnvironments: List<CCloudEnvironment>? = null
    private val cachedClusters = mutableMapOf<String, List<KafkaCluster>>()
    private val cachedSchemaRegistries = mutableMapOf<String, List<SchemaRegistry>>()

    override fun getRealUri(): String = CloudConfig.CONTROL_PLANE_BASE_URL

    override fun connectInner(calledByUser: Boolean) {
        if (!connectionData.hasCredentials()) {
            throw IllegalStateException("API credentials not configured")
        }
        fetcher = CloudFetcherImpl(connectionData.apiKey, connectionData.apiSecret)
    }

    override fun checkConnectionInner() {
        val f = fetcher ?: throw IllegalStateException("Client not initialized")
        // Validate connection by fetching environments
        cachedEnvironments = runBlocking {
            f.getEnvironments(CONNECTION_ID)
        }
    }

    fun getEnvironments(): List<CCloudEnvironment> = cachedEnvironments ?: emptyList()

    fun getKafkaClusters(environmentId: String): List<KafkaCluster> = cachedClusters.getOrPut(environmentId) {
        fetcher?.let { f ->
            runBlocking { f.getKafkaClusters(CONNECTION_ID, environmentId) }
        } ?: emptyList()
    }

    fun getSchemaRegistries(environmentId: String): List<SchemaRegistry> = cachedSchemaRegistries.getOrPut(environmentId) {
        fetcher?.let { f ->
            runBlocking { f.getSchemaRegistries(CONNECTION_ID, environmentId) }
        } ?: emptyList()
    }

    fun refreshEnvironments(): List<CCloudEnvironment> {
        cachedEnvironments = fetcher?.let { f ->
            runBlocking { f.getEnvironments(CONNECTION_ID) }
        } ?: emptyList()
        return cachedEnvironments ?: emptyList()
    }

    fun refreshClusters(environmentId: String): List<KafkaCluster> {
        val clusters = fetcher?.let { f ->
            runBlocking { f.getKafkaClusters(CONNECTION_ID, environmentId) }
        } ?: emptyList()
        cachedClusters[environmentId] = clusters
        return clusters
    }

    fun refreshSchemaRegistries(environmentId: String): List<SchemaRegistry> {
        val registries = fetcher?.let { f ->
            runBlocking { f.getSchemaRegistries(CONNECTION_ID, environmentId) }
        } ?: emptyList()
        cachedSchemaRegistries[environmentId] = registries
        return registries
    }

    companion object {
        private const val CONNECTION_ID = "confluent-toolwindow"
    }

    fun clearCache() {
        cachedEnvironments = null
        cachedClusters.clear()
        cachedSchemaRegistries.clear()
    }

    override fun dispose() {
        fetcher = null
        clearCache()
    }
}

