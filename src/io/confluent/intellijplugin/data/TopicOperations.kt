package io.confluent.intellijplugin.data

/**
 * Interface for topic operations (create, delete, clear, and configuration).
 * Implemented by both KafkaDataManager (native protocol) and ClusterScopedDataManager (CCloud REST API).
 */
interface TopicOperations {
    /**
     * Create a new topic.
     *
     * @param name Topic name
     * @param partitions Number of partitions
     * @param replicationFactor Replication factor
     * @param configs Optional topic configurations
     */
    suspend fun createTopic(
        name: String,
        partitions: Int?,
        replicationFactor: Int?,
        configs: Map<String, String> = emptyMap()
    ): Result<Unit>

    /**
     * Delete one or more topics.
     */
    suspend fun deleteTopic(topicNames: List<String>): Result<Unit>

    /**
     * Clear all messages from a topic (if supported).
     */
    suspend fun clearTopic(topicName: String): Result<Unit>
}
