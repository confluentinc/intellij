package io.confluent.intellijplugin.data

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import io.confluent.intellijplugin.ccloud.cache.ControlPlaneCache
import io.confluent.intellijplugin.ccloud.cache.DataPlaneCache
import io.confluent.intellijplugin.ccloud.model.Cluster
import io.confluent.intellijplugin.ccloud.model.Environment
import io.confluent.intellijplugin.ccloud.model.SchemaRegistry
import io.confluent.intellijplugin.core.connection.updater.IntervalUpdateSettings
import io.confluent.intellijplugin.core.monitoring.data.MonitoringDataManager
import io.confluent.intellijplugin.core.monitoring.rfs.MonitoringDriver
import io.confluent.intellijplugin.rfs.ConfluentConnectionData

/**
 * Organization-level manager for Confluent Cloud.
 *
 * Coordinates access to multiple Kafka clusters within a Confluent Cloud organization.
 * Manages two-tier caching:
 * - Control plane: One ControlPlaneCache for organizational resources (environments, clusters)
 * - Data plane: One DataPlaneCache per cluster for cluster resources (topics, schemas, consumer groups)
 *
 * This manager creates and maintains [CCloudClusterDataManager] instances for each cluster.
 */
class CCloudOrgManager(
    project: Project?,
    override val connectionData: ConfluentConnectionData,
    override val settings: IntervalUpdateSettings,
    driverProvider: () -> MonitoringDriver
) : MonitoringDataManager(project, settings, driverProvider) {

    override val client = ControlPlaneCache(project).also {
        Disposer.register(this, it)
    }

    private val dataPlaneCache = mutableMapOf<String, DataPlaneCache>()

    private val clusterDataManagers = mutableMapOf<String, CCloudClusterDataManager>()

    fun getDataPlaneCache(cluster: Cluster): DataPlaneCache {
        return dataPlaneCache.getOrPut(cluster.id) {
            val schemaRegistry = findSchemaRegistryForCluster(cluster)
            val cache = DataPlaneCache(cluster, schemaRegistry)
            cache.connect()
            Disposer.register(this, cache)
            cache
        }
    }

    fun getOrCreateClusterDataManager(cluster: Cluster): CCloudClusterDataManager {
        return clusterDataManagers.getOrPut(cluster.id) {
            CCloudClusterDataManager(project, this, cluster).also {
                Disposer.register(this, it)
            }
        }
    }

    fun getAllClusterDataManagers(): Collection<CCloudClusterDataManager> = clusterDataManagers.values

    /** Find Schema Registry for a cluster's environment. */
    private fun findSchemaRegistryForCluster(cluster: Cluster): SchemaRegistry? {
        val environments = client.getEnvironments()
        for (env in environments) {
            val clustersInEnv = client.getKafkaClusters(env.id)
            if (clustersInEnv.any { it.id == cluster.id }) {
                return client.getSchemaRegistry(env.id)
            }
        }
        return null
    }

    fun getEnvironments(): List<Environment> = client.getEnvironments()
    fun getKafkaClusters(environmentId: String): List<Cluster> = client.getKafkaClusters(environmentId)
    fun getSchemaRegistry(environmentId: String): SchemaRegistry? = client.getSchemaRegistry(environmentId)

    init {
        init()
    }

    override fun dispose() {
        dataPlaneCache.clear()
        super.dispose()
    }
}
