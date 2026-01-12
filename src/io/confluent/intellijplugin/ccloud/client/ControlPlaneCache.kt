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
 * Control plane cache for organizational resources (environments, clusters, schema registries).
 *
 * Caches data from CloudFetcherImpl to reduce API calls. Use refresh*() methods to update cache.
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

    /** Get cached environments (empty if not loaded). */
    fun getEnvironments(): List<Environment> = cachedEnvironments ?: emptyList()

    /** Get clusters for an environment (fetches on first access, then cached). */
    fun getKafkaClusters(environmentId: String): List<Cluster> = cachedClusters.getOrPut(environmentId) {
        fetcher?.let { f ->
            runBlocking { f.getKafkaClusters(environmentId) }
        } ?: emptyList()
    }

    /** Get schema registries for an environment (fetches on first access, then cached). */
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
