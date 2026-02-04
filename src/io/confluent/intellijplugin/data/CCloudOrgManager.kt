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

    init { init() }

    fun getDataPlaneCache(cluster: Cluster): DataPlaneCache =
        dataPlaneCache.getOrPut(cluster.id) {
            DataPlaneCache(cluster, findSchemaRegistryForCluster(cluster)).also {
                it.connect()
                Disposer.register(this, it)
            }
        }

    fun getOrCreateClusterDataManager(cluster: Cluster): CCloudClusterDataManager =
        clusterDataManagers.getOrPut(cluster.id) {
            CCloudClusterDataManager(project, this, cluster).also { Disposer.register(this, it) }
        }

    fun getAllClusterDataManagers(): Collection<CCloudClusterDataManager> = clusterDataManagers.values

    /** Finds Schema Registry for the cluster's environment. */
    private fun findSchemaRegistryForCluster(cluster: Cluster): SchemaRegistry? =
        client.getEnvironments().firstNotNullOfOrNull { env ->
            if (client.getKafkaClusters(env.id).any { it.id == cluster.id }) {
                client.getSchemaRegistry(env.id)
            } else null
        }

    fun getEnvironments(): List<Environment> = client.getEnvironments()
    fun getKafkaClusters(environmentId: String): List<Cluster> = client.getKafkaClusters(environmentId)
    fun getSchemaRegistry(environmentId: String): SchemaRegistry? = client.getSchemaRegistry(environmentId)
}
