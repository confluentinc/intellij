package io.confluent.intellijplugin.data

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import io.confluent.intellijplugin.ccloud.cache.DataPlaneCache
import io.confluent.intellijplugin.ccloud.model.Cluster
import io.confluent.intellijplugin.ccloud.model.response.TopicData
import io.confluent.intellijplugin.core.monitoring.data.MonitoringDataManager
import io.confluent.intellijplugin.core.monitoring.data.model.ObjectDataModel
import io.confluent.intellijplugin.core.monitoring.data.updater.BdtMonitoringUpdater
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
), TopicDataProvider {

    private val dataPlaneCache: DataPlaneCache = confluentDataManager.getDataPlaneCache(cluster)

    /**
     * Get the data plane cache for this cluster.
     * Used by CCloudConsumerClient to access the fetcher.
     */
    fun getDataPlaneCache(): DataPlaneCache = dataPlaneCache

    /**
     * Connection ID for this cluster scope.
     * Uses cluster ID to maintain separate settings per cluster.
     */
    override val connectionId: String = cluster.id

    /**
     * Override connection data to provide cluster-specific connection.
     */
    override val connectionData: ConfluentConnectionData
        get() = confluentDataManager.connectionData

    /**
     * Topic model for this specific cluster.
     */
    override val topicModel: ObjectDataModel<TopicPresentable> = createTopicsDataModel().also {
        Disposer.register(this, it)
    }

    /**
     * Reuse the parent client.
     */
    override val client
        get() = confluentDataManager.client

    /**
     * Get topics for this cluster.
     */
    override fun getTopics(): List<TopicPresentable> = topicModel.data ?: emptyList()

    /**
     * Convert TopicData from DataPlaneCache to TopicPresentable for UI display.
     */
    private fun TopicData.toTopicPresentable(isFavorite: Boolean = false): TopicPresentable {
        return TopicPresentable(
            name = topicName,
            internal = isInternal,
            partitions = partitionsCount,
            replicationFactor = replicationFactor,
            isFavorite = isFavorite
        )
    }

    /**
     * Create topic data model that fetches from DataPlaneCache.
     */
    private fun createTopicsDataModel() = ObjectDataModel(TopicPresentable::name) {
        val toolWindowSettings = KafkaToolWindowSettings.getInstance()
        val config = toolWindowSettings.getOrCreateConfig(connectionId)
        val topicFilterName = config.topicFilterName

        // Refresh topics from data plane cache
        val topicDataList = dataPlaneCache.refreshTopics()

        // Convert to TopicPresentable and apply filters
        val topics = topicDataList
            .filter { topicData ->
                // Filter internal topics if needed
                val showInternal = toolWindowSettings.showInternalTopics
                (showInternal || !topicData.isInternal) &&
                // Apply name filter
                (topicFilterName == null || topicData.topicName.lowercase().contains(topicFilterName.lowercase()))
            }
            .map { topicData ->
                val isFavorite = config.topicsPined.contains(topicData.topicName)
                topicData.toTopicPresentable(isFavorite)
            }

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
        updater.invokeRefreshModel(topicModel)
    }

    override fun dispose() {
        // Don't dispose the parent ConfluentDataManager, just clean up our references
    }
}
