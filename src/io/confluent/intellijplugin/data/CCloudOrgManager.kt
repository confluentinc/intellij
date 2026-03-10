package io.confluent.intellijplugin.data

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import io.confluent.intellijplugin.ccloud.cache.ControlPlaneCache
import io.confluent.intellijplugin.ccloud.cache.DataPlaneCache
import io.confluent.intellijplugin.ccloud.config.CloudConfig
import io.confluent.intellijplugin.ccloud.model.Cluster
import io.confluent.intellijplugin.ccloud.model.Environment
import io.confluent.intellijplugin.ccloud.model.SchemaRegistry
import io.confluent.intellijplugin.core.connection.updater.IntervalUpdateSettings
import io.confluent.intellijplugin.core.monitoring.data.MonitoringDataManager
import io.confluent.intellijplugin.core.monitoring.data.model.DataModel
import io.confluent.intellijplugin.core.monitoring.data.storage.DataModelStorage
import io.confluent.intellijplugin.core.monitoring.data.updater.BdtMonitoringUpdater
import io.confluent.intellijplugin.core.monitoring.rfs.MonitoringDriver
import io.confluent.intellijplugin.rfs.ConfluentConnectionData
import io.confluent.intellijplugin.rfs.ConfluentDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore

/**
 * Organization-level manager for Confluent Cloud.
 *
 * Manages two-tier caching architecture:
 * - Control plane: Org-level resources (environments, clusters)
 * - Data plane: Per-cluster resources (topics, schemas, consumer groups)
 *
 * Creates and maintains [CCloudClusterDataManager] instances for each cluster.
 */
class CCloudOrgManager(
    project: Project?,
    override val connectionData: ConfluentConnectionData,
    override val settings: IntervalUpdateSettings,
    driverProvider: () -> MonitoringDriver
) : MonitoringDataManager(project, settings, driverProvider) {

    override val client = ControlPlaneCache(project).also { Disposer.register(this, it) }

    private val dataPlaneCache = mutableMapOf<String, DataPlaneCache>()
    private val clusterDataManagers = mutableMapOf<String, CCloudClusterDataManager>()

    init {
        init()
        // Register dynamic storage that returns cluster models for auto-refresh
        CCloudDynamicModelStorage(updater, this).also { Disposer.register(this, it) }
    }

    fun getDataPlaneCache(cluster: Cluster): DataPlaneCache =
        dataPlaneCache.getOrPut(cluster.id) {
            DataPlaneCache(cluster, findSchemaRegistryForCluster(cluster)).also {
                it.connect()
                Disposer.register(this, it)
            }
        }

    /**
     * Pre-initializes caches for an environment using progressive loading.
     * Fetches clusters/SR first, invokes callback to show tree, then fetches topics/schemas in background.
     */
    fun preInitializeCachesForEnvironment(environmentId: String, onComplete: (() -> Unit)? = null) {
        driver.coroutineScope.launch(Dispatchers.IO) {
            try {
                coroutineScope {
                    val clustersDeferred = async {
                        try {
                            client.refreshKafkaClusters(environmentId)
                        } catch (e: Exception) {
                            thisLogger().warn("Failed to fetch clusters for environment $environmentId: ${e.message}")
                            emptyList()
                        }
                    }

                    val srDeferred = async {
                        try {
                            client.refreshSchemaRegistry(environmentId)
                        } catch (e: Exception) {
                            thisLogger().warn("Failed to fetch schema registry for environment $environmentId: ${e.message}")
                            null
                        }
                    }

                    val clusters = clustersDeferred.await()
                    srDeferred.await()

                    onComplete?.let { callback ->
                        ApplicationManager.getApplication().invokeLater(callback)
                    }

                    if (clusters.isNotEmpty()) {
                        val semaphore = Semaphore(CloudConfig.API_RATE_LIMIT)

                        clusters.map { cluster ->
                            async {
                                semaphore.acquire()
                                try {
                                    val cache = getDataPlaneCache(cluster)
                                    cache.refreshTopics()
                                    if (cache.hasSchemaRegistry()) {
                                        cache.refreshSchemas()
                                    }
                                } catch (e: Exception) {
                                    thisLogger().warn("Failed to pre-fetch cluster ${cluster.id}: ${e.message}")
                                } finally {
                                    semaphore.release()
                                }
                            }
                        }.awaitAll()
                    }
                }
            } catch (e: Exception) {
                thisLogger().warn("Failed to pre-initialize caches for environment $environmentId", e)
            }
        }
    }

    fun getOrCreateClusterDataManager(cluster: Cluster): CCloudClusterDataManager =
        clusterDataManagers.getOrPut(cluster.id) {
            CCloudClusterDataManager(project, this, cluster).also { Disposer.register(this, it) }
        }

    fun getAllClusterDataManagers(): Collection<CCloudClusterDataManager> = clusterDataManagers.values

    private fun findSchemaRegistryForCluster(cluster: Cluster): SchemaRegistry? =
        client.getEnvironments().firstNotNullOfOrNull { env ->
            client.getCachedKafkaClusters(env.id)?.let { clusters ->
                if (clusters.any { it.id == cluster.id }) {
                    client.getCachedSchemaRegistry(env.id)
                } else null
            }
        }

    fun getEnvironments(): List<Environment> = client.getEnvironments()
    fun getKafkaClusters(environmentId: String): List<Cluster> = client.getKafkaClusters(environmentId)
    fun getSchemaRegistry(environmentId: String): SchemaRegistry? = client.getSchemaRegistry(environmentId)

    /** Cancel all ongoing enrichment jobs across all cluster data managers. */
    fun cancelAllEnrichmentJobs() {
        clusterDataManagers.values.forEach { it.cancelAllEnrichmentJobs() }
    }

    // CCloud org-level has no models, so skip automatic connection check on model refresh
    override fun checkConnectionOnRefresh() = false

    override fun onSuccessfulConnect() {
        super.onSuccessfulConnect()
        // Trigger cluster-level model refresh after connection succeeds
        val models = getClusterModelsForRefresh()
        if (models.isNotEmpty()) {
            updater.invokeRefreshModels(models)
        }
    }

    /**
     * Get cluster models for the currently selected environment.
     * Used for both manual refresh and auto-refresh (via CCloudDynamicModelStorage).
     */
    internal fun getClusterModelsForRefresh(): List<DataModel<*>> {
        val selectedEnvId = (driver as? ConfluentDriver)?.selectedEnvironmentId
            ?: return emptyList()

        return getKafkaClusters(selectedEnvId).flatMap { cluster ->
            val clusterManager = getOrCreateClusterDataManager(cluster)
            listOfNotNull(clusterManager.topicModel, clusterManager.schemaRegistryModel)
        }
    }
}

/**
 * Dynamic storage for CCloud that provides cluster models for auto-refresh.
 * Unlike Kafka which has a single-tier architecture with all models in one manager,
 * CCloud has org-level and cluster-level managers. This storage bridges the gap
 * by dynamically returning cluster models to the org-level updater.
 */
private class CCloudDynamicModelStorage(
    updater: BdtMonitoringUpdater,
    private val orgManager: CCloudOrgManager
) : DataModelStorage(updater) {

    init {
        init()
    }

    override fun getModelsForRefresh() = orgManager.getClusterModelsForRefresh()

    override fun getAsMap(): Map<*, DataModel<*>> {
        // Not used for cleanup since cluster managers own their models
        return mapOf<Any, DataModel<*>>()
    }

    override fun clearSelected(list: List<Any?>) {
        // Not used - cluster managers handle their own cleanup
    }
}
