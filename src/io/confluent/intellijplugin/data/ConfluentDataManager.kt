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
 * Data manager for Confluent Cloud resources.
 *
 * Manages two-tier caching:
 * - Control plane: One ControlPlaneCache for organizational resources
 * - Data plane: One DataPlaneCache per cluster for topics/subjects/consumer groups
 */
class ConfluentDataManager(
    project: Project?,
    override val connectionData: ConfluentConnectionData,
    override val settings: IntervalUpdateSettings,
    driverProvider: () -> MonitoringDriver
) : MonitoringDataManager(project, settings, driverProvider) {

    override val client = ControlPlaneCache(project).also {
        Disposer.register(this, it)
    }

    private val dataPlaneCache = mutableMapOf<String, DataPlaneCache>()

    private val clusterDataManagers = mutableMapOf<String, ClusterScopedDataManager>()

    fun getDataPlaneCache(cluster: Cluster): DataPlaneCache {
        return dataPlaneCache.getOrPut(cluster.id) {
            val schemaRegistry = findSchemaRegistryForCluster(cluster)
            val cache = DataPlaneCache(cluster, schemaRegistry)
            cache.connect()
            Disposer.register(this, cache)
            cache
        }
    }

    fun getOrCreateClusterDataManager(cluster: Cluster): ClusterScopedDataManager {
        return clusterDataManagers.getOrPut(cluster.id) {
            ClusterScopedDataManager(project, this, cluster).also {
                Disposer.register(this, it)
            }
        }
    }

    fun getAllClusterDataManagers(): Collection<ClusterScopedDataManager> = clusterDataManagers.values

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

    // ========== Lifecycle ==========

    init {
        init()
    }

    override fun dispose() {
        dataPlaneCache.clear()
        super.dispose()
    }
}
