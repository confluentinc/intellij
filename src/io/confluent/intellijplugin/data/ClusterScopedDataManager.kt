package io.confluent.intellijplugin.data

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import io.confluent.intellijplugin.ccloud.cache.DataPlaneCache
import io.confluent.intellijplugin.ccloud.model.Cluster
import io.confluent.intellijplugin.ccloud.model.response.TopicData
import io.confluent.intellijplugin.ccloud.model.response.toPresentable
import io.confluent.intellijplugin.core.monitoring.data.MonitoringDataManager
import io.confluent.intellijplugin.core.monitoring.data.model.ObjectDataModel
import io.confluent.intellijplugin.core.monitoring.data.updater.BdtMonitoringUpdater
import io.confluent.intellijplugin.core.monitoring.data.storage.RootDataModelStorage
import io.confluent.intellijplugin.model.TopicPresentable
import io.confluent.intellijplugin.rfs.ConfluentConnectionData
import io.confluent.intellijplugin.toolwindow.config.KafkaToolWindowSettings

/**
 * Cluster-scoped wrapper around ConfluentDataManager that presents the interface
 * expected by TopicsController (designed for KafkaDataManager).
 *
 * This allows reuse of TopicsController for Confluent Cloud clusters.
 */
class ClusterScopedDataManager(
    project: Project?,
    private val confluentDataManager: ConfluentDataManager,
    private val cluster: Cluster
) : MonitoringDataManager(
    project,
    confluentDataManager.settings,
    { confluentDataManager.driver }
), TopicDataProvider, TopicOperations {

    private val dataPlaneCache: DataPlaneCache = confluentDataManager.getDataPlaneCache(cluster)

    override val connectionId: String = cluster.id

    override val connectionData: ConfluentConnectionData
        get() = confluentDataManager.connectionData

    override val topicModel: ObjectDataModel<TopicPresentable> = createTopicsDataModel().also {
        Disposer.register(this, it)
    }

    override val client
        get() = confluentDataManager.client

    init {
        RootDataModelStorage(confluentDataManager.updater, listOf(topicModel))
    }

    /**
     * Get topics for this cluster.
     */
    override fun getTopics(): List<TopicPresentable> = topicModel.data ?: emptyList()

    /**
     * Create topic data model with enrichment (message count).
     */
    private fun createTopicsDataModel() = ObjectDataModel(
        idFieldName = TopicPresentable::name,
        additionalInfoLoading = { model ->
            val basicTopics = model.data ?: emptyList()
            val topicDataList = dataPlaneCache.getTopics()

            if (topicDataList.isEmpty()) {
                return@ObjectDataModel basicTopics to null
            }

            try {
                // Enrich with message count
                thisLogger().info("Starting enrichment for ${topicDataList.size} topics in cluster ${cluster.id}")
                val enrichmentMap = dataPlaneCache.enrichTopicsData(topicDataList)
                thisLogger().info("Enrichment completed: received ${enrichmentMap.size} results")

                val enrichedTopics = basicTopics.map { topic ->
                    enrichmentMap[topic.name]?.let { enrichment ->
                        topic.copy(
                            messageCount = enrichment.messageCount
                        )
                    } ?: topic
                }

                thisLogger().info("Applied enrichment to ${enrichedTopics.count { it.messageCount != null }} topics with message counts")
                enrichedTopics to null
            } catch (t: Throwable) {
                thisLogger().warn("Failed to enrich topic data for cluster ${cluster.id}", t)
                basicTopics to t
            }
        }
    ) {
        val toolWindowSettings = KafkaToolWindowSettings.getInstance()
        val config = toolWindowSettings.getOrCreateConfig(connectionId)
        val topicFilterName = config.topicFilterName

        // Refresh topics from data plane cache
        val topicDataList = dataPlaneCache.refreshTopics()

        // Convert to TopicPresentable, apply filters, and sort
        val allTopics = topicDataList
            .filter { topicData ->
                // Filter internal topics
                val showInternal = toolWindowSettings.showInternalTopics
                (showInternal || !topicData.isInternal) &&
                // Apply name filter
                (topicFilterName == null || topicData.topicName.lowercase().contains(topicFilterName.lowercase()))
            }
            .map { topicData ->
                val isFavorite = config.topicsPined.contains(topicData.topicName)
                topicData.toPresentable().copy(isFavorite = isFavorite)
            }

        // Filter favorites if needed, then sort: favorites first, then alphabetically
        val topics = if (toolWindowSettings.showFavoriteTopics) {
            allTopics.filter { it.isFavorite }
        } else {
            allTopics
        }.sortedWith(compareByDescending<TopicPresentable> { it.isFavorite }.thenBy { it.name.lowercase() })

        // Apply topic limit
        val topicLimit = config.topicLimit
        (topicLimit?.let { topics.take(it) } ?: topics) to (topicLimit != null && topics.size > topicLimit)
    }

    /**
     * Update favorite topics for this cluster.
     * Stores favorites per cluster ID.
     */
    override fun updatePinedTopics(topicName: String, isForAdding: Boolean) {
        val config = KafkaToolWindowSettings.getInstance().getOrCreateConfig(connectionId)
        if (isForAdding) {
            config.topicsPined += topicName
        } else {
            config.topicsPined -= topicName
        }
        confluentDataManager.updater.invokeRefreshModel(topicModel)
    }

    // ========== TopicOperations Implementation ==========

    override suspend fun createTopic(
        name: String,
        partitions: Int,
        replicationFactor: Int,
        configs: Map<String, String>
    ): Result<Unit> {
        TODO("Implement in Phase 2")
    }

    override suspend fun deleteTopic(topicNames: List<String>): Result<Unit> {
        TODO("Implement in Phase 3")
    }

    override suspend fun clearTopic(topicName: String): Result<Unit> {
        // CCloud doesn't support clearing topics via REST API
        return Result.failure(UnsupportedOperationException("Clear topic not supported for Confluent Cloud"))
    }

    override suspend fun describeTopic(topicName: String): TopicDetails {
        TODO("Implement in Phase 4")
    }

    override suspend fun describeTopicPartitions(topicName: String): List<PartitionDetails> {
        TODO("Implement in Phase 5")
    }

    override suspend fun describeTopicConfiguration(topicName: String): Map<String, ConfigEntry> {
        TODO("Implement in Phase 6")
    }

    /**
     * CCloud REST API does not expose replicas endpoint, so ISR data is not available.
     */
    override fun supportsInSyncReplicasData(): Boolean = false

    override fun dispose() {
    }
}
