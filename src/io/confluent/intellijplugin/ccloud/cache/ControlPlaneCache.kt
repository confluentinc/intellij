package io.confluent.intellijplugin.ccloud.cache

import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import io.confluent.intellijplugin.ccloud.client.CCloudRestClient
import io.confluent.intellijplugin.ccloud.config.CloudConfig
import io.confluent.intellijplugin.ccloud.fetcher.ControlPlaneFetcherImpl
import io.confluent.intellijplugin.ccloud.model.Cluster
import io.confluent.intellijplugin.ccloud.model.Environment
import io.confluent.intellijplugin.ccloud.model.SchemaRegistry
import io.confluent.intellijplugin.core.monitoring.connection.MonitoringClient

/**
 * Control plane cache for organizational resources (environments, clusters, schema registries).
 * Caches data from ControlPlaneFetcherImpl to reduce API calls. Use refresh*() methods to update cache.
 */
class ControlPlaneCache(
    project: Project?
) : MonitoringClient(project) {

    private var fetcher: ControlPlaneFetcherImpl? = null

    // Cached data
    private var cachedEnvironments: List<Environment>? = null
    private val cachedClusters = mutableMapOf<String, List<Cluster>>()
    private val cachedSchemaRegistry = mutableMapOf<String, SchemaRegistry?>()

    override fun getRealUri(): String = CloudConfig.CONTROL_PLANE_BASE_URL

    override fun connectInner(calledByUser: Boolean) {
        val client = CCloudRestClient(
            baseUrl = CloudConfig.CONTROL_PLANE_BASE_URL,
            authType = CCloudRestClient.AuthType.CONTROL_PLANE
        )
        fetcher = ControlPlaneFetcherImpl(client)
    }

    override fun checkConnectionInner() {
        val f = fetcher ?: throw IllegalStateException("Cache not initialized")
        // Validate connection by fetching environments
        cachedEnvironments = runBlockingMaybeCancellable {
            f.getEnvironments()
        }
    }

    /** Get cached environments (empty if not loaded). */
    fun getEnvironments(): List<Environment> = cachedEnvironments ?: emptyList()

    /** Get clusters for an environment (fetches on first access, then cached). */
    fun getKafkaClusters(environmentId: String): List<Cluster> = cachedClusters.getOrPut(environmentId) {
        fetcher?.let { f ->
            runBlockingMaybeCancellable { f.getKafkaClusters(environmentId) }
        } ?: emptyList()
    }

    fun getCachedKafkaClusters(environmentId: String): List<Cluster>? = cachedClusters[environmentId]

    /** Get schema registry for an environment (fetches on first access, then cached). Returns null if none exists. */
    fun getSchemaRegistry(environmentId: String): SchemaRegistry? = cachedSchemaRegistry.getOrPut(environmentId) {
        fetcher?.let { f ->
            runBlockingMaybeCancellable { f.getSchemaRegistry(environmentId) }
        }
    }

    fun getCachedSchemaRegistry(environmentId: String): SchemaRegistry? = cachedSchemaRegistry[environmentId]

    suspend fun refreshEnvironments(): List<Environment> {
        cachedEnvironments = fetcher?.let { f ->
            f.getEnvironments()
        } ?: emptyList()
        return cachedEnvironments ?: emptyList()
    }

    suspend fun refreshKafkaClusters(environmentId: String): List<Cluster> {
        val clusters = fetcher?.let { f ->
            f.getKafkaClusters(environmentId)
        } ?: emptyList()
        cachedClusters[environmentId] = clusters
        return clusters
    }

    suspend fun refreshSchemaRegistry(environmentId: String): SchemaRegistry? {
        val registry = fetcher?.let { f ->
            f.getSchemaRegistry(environmentId)
        }
        cachedSchemaRegistry[environmentId] = registry
        return registry
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

