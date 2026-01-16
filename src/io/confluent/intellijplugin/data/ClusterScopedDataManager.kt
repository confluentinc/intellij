package io.confluent.intellijplugin.data

import com.intellij.openapi.project.Project
import io.confluent.intellijplugin.ccloud.model.Cluster
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
    override val topicModel: ObjectDataModel<TopicPresentable>
        get() = confluentDataManager.getTopicModel(cluster)

    /**
     * Reuse the parent client.
     */
    override val client
        get() = confluentDataManager.client

    /**
     * Get topics for this cluster.
     */
    override fun getTopics(): List<TopicPresentable> = confluentDataManager.getTopics(cluster)

    /**
     * Update pinned (favorite) topics for this cluster.
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
