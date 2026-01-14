package io.confluent.intellijplugin.data

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import io.confluent.intellijplugin.ccloud.cache.ControlPlaneCache
import io.confluent.intellijplugin.ccloud.cache.DataPlaneCache
import io.confluent.intellijplugin.ccloud.model.Cluster
import io.confluent.intellijplugin.ccloud.model.Environment
import io.confluent.intellijplugin.ccloud.model.SchemaRegistry
import io.confluent.intellijplugin.ccloud.model.response.toPresentable
import io.confluent.intellijplugin.ccloud.model.response.toPresentableWithEnrichment
import io.confluent.intellijplugin.core.connection.updater.IntervalUpdateSettings
import io.confluent.intellijplugin.core.monitoring.data.MonitoringDataManager
import io.confluent.intellijplugin.core.monitoring.data.model.ObjectDataModel
import io.confluent.intellijplugin.core.monitoring.rfs.MonitoringDriver
import io.confluent.intellijplugin.model.TopicPresentable
import io.confluent.intellijplugin.rfs.ConfluentConnectionData

/**
 * Manages Confluent Cloud resources with two-tier caching:
 * - Control plane: Organizational resources (environments, clusters, schema registries)
 * - Data plane: Cluster resources (topics, subjects, consumer groups)
 */
class ConfluentDataManager(
    project: Project?,
    override val connectionData: ConfluentConnectionData,
    override val settings: IntervalUpdateSettings,
    driverProvider: () -> MonitoringDriver
) : MonitoringDataManager(project, settings, driverProvider) {

    override val client = ControlPlaneCache(project, connectionData).also {
        Disposer.register(this, it)
    }

    private val dataPlaneCache = mutableMapOf<String, DataPlaneCache>()
    private val topicModels = mutableMapOf<String, ObjectDataModel<TopicPresentable>>()

    /** Raw API data per cluster, used for enrichment. */
    private val cachedTopicData = mutableMapOf<String, List<io.confluent.intellijplugin.ccloud.model.response.TopicData>>()

    /** Gets or creates data plane cache for a cluster. */
    fun getDataPlaneCache(cluster: Cluster): DataPlaneCache {
        return dataPlaneCache.getOrPut(cluster.id) {
            val schemaRegistry = findSchemaRegistryForCluster(cluster)
            val cache = DataPlaneCache(cluster, schemaRegistry)
            cache.connect()
            Disposer.register(this, cache)
            cache
        }
    }

    /** Gets or creates topic data model for a cluster. */
    fun getTopicModel(cluster: Cluster): ObjectDataModel<TopicPresentable> {
        return topicModels.getOrPut(cluster.id) {
            createTopicDataModel(cluster).also { Disposer.register(this, it) }
        }
    }

    private fun createTopicDataModel(cluster: Cluster) = ObjectDataModel(
        idFieldName = TopicPresentable::name,
        additionalInfoLoading = { model ->
            try {
                val basicTopics = model.data ?: emptyList()
                val topicDataList = cachedTopicData[cluster.id] ?: emptyList()

                if (topicDataList.isEmpty()) {
                    return@ObjectDataModel basicTopics to null
                }

                // Enrich with message count
                val cache = getDataPlaneCache(cluster)
                val enrichmentData = cache.enrichTopicsData(topicDataList)

                val enrichedTopics = basicTopics.map { topic ->
                    enrichmentData[topic.name]?.let { enrichment ->
                        topic.copy(
                            messageCount = enrichment.messageCount,
                            inSyncReplicas = enrichment.inSyncReplicas
                        )
                    } ?: topic
                }

                enrichedTopics to null
            } catch (t: Throwable) {
                thisLogger().warn("Failed to enrich topic data for cluster ${cluster.id}", t)
                (model.data ?: emptyList()) to t
            }
        }
    ) {
        val cache = getDataPlaneCache(cluster)
        val topics = cache.refreshTopics()

        cachedTopicData[cluster.id] = topics
        topics.toPresentable() to false
    }

    /** Finds Schema Registry for a cluster's environment. */
    private fun findSchemaRegistryForCluster(cluster: Cluster): SchemaRegistry? {
        val environments = client.getEnvironments()
        for (env in environments) {
            val clustersInEnv = client.getKafkaClusters(env.id)
            if (clustersInEnv.any { it.id == cluster.id }) {
                return client.getSchemaRegistry(env.id).firstOrNull()
            }
        }
        return null
    }

    fun getEnvironments(): List<Environment> = client.getEnvironments()
    fun getKafkaClusters(environmentId: String): List<Cluster> = client.getKafkaClusters(environmentId)
    fun getSchemaRegistry(environmentId: String): List<SchemaRegistry> = client.getSchemaRegistry(environmentId)

    fun getTopics(cluster: Cluster): List<TopicPresentable> = getTopicModel(cluster).data ?: emptyList()

    fun getCachedTopicByName(cluster: Cluster, name: String): TopicPresentable? =
        getTopics(cluster).firstOrNull { it.name == name }

    init {
        init()
    }

    override fun dispose() {
        dataPlaneCache.clear()
        topicModels.clear()
        cachedTopicData.clear()
        super.dispose()
    }
}
