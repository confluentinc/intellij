package io.confluent.intellijplugin.data

/**
 * Interface for topic mutation operations (create, delete, describe).
 * Implemented by both KafkaDataManager (native protocol) and ConfluentDataManager (REST API).
 */
interface TopicOperations {
    /**
     * Create a new topic.
     */
    suspend fun createTopic(
        name: String,
        partitions: Int,
        replicationFactor: Int,
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

    /**
     * Get detailed information about a topic.
     */
    suspend fun describeTopic(topicName: String): TopicDetails

    /**
     * Get partition-level details for a topic.
     */
    suspend fun describeTopicPartitions(topicName: String): List<PartitionDetails>

    /**
     * Get configuration entries for a topic.
     */
    suspend fun describeTopicConfiguration(topicName: String): Map<String, ConfigEntry>
}

/**
 * Topic details (name, partitions, replication, message count, ISRs).
 */
data class TopicDetails(
    val name: String,
    val isInternal: Boolean,
    val partitionsCount: Int,
    val replicationFactor: Int,
    val messageCount: Long? = null,
    val inSyncReplicas: Int? = null
)

/**
 * Partition details (ID, leader, replicas, offsets, message count).
 */
data class PartitionDetails(
    val partitionId: Int,
    val leader: Int?,
    val replicasCount: Int,
    val messageCount: Long? = null,
    val beginOffset: Long? = null,
    val endOffset: Long? = null
)

/**
 * Configuration entry (name, value, source, flags).
 */
data class ConfigEntry(
    val name: String,
    val value: String?,
    val isDefault: Boolean,
    val isReadOnly: Boolean,
    val isSensitive: Boolean,
    val source: String
)
