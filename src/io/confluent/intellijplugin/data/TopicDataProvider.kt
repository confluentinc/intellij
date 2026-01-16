package io.confluent.intellijplugin.data

import io.confluent.intellijplugin.core.monitoring.data.model.ObjectDataModel
import io.confluent.intellijplugin.core.monitoring.data.updater.BdtMonitoringUpdater
import io.confluent.intellijplugin.model.TopicPresentable

/**
 * Interface for data managers that provide topic-related data and operations.
 * Implemented by both KafkaDataManager and ClusterScopedDataManager to enable
 * reuse of TopicsController across different connection types.
 */
interface TopicDataProvider {
    /**
     * Connection ID for this topic provider scope.
     */
    val connectionId: String

    /**
     * Data model for topics.
     */
    val topicModel: ObjectDataModel<TopicPresentable>

    /**
     * Updater for refreshing data models.
     */
    val updater: BdtMonitoringUpdater

    /**
     * Get the list of topics.
     */
    fun getTopics(): List<TopicPresentable>

    /**
     * Update pinned (favorite) status for a topic.
     */
    fun updatePinedTopics(topicName: String, isForAdding: Boolean)
}
