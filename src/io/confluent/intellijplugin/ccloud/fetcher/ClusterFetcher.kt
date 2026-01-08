package io.confluent.intellijplugin.ccloud.fetcher

import io.confluent.intellijplugin.ccloud.model.response.TopicData

/**
 * Interface for Kafka cluster data plane operations via REST API v3.
 *
 * Use for: List/describe topics, configs, partitions.
 * For produce/consume: Use native Kafka protocol via KafkaClient.
 *
 * @see <a href="https://docs.confluent.io/cloud/current/api.html#tag/Topic-(v3)">Kafka REST API v3</a>
 */
interface ClusterFetcher {
    // Topics

    /**
     * List all topics in the cluster.
     * @see <a href="https://docs.confluent.io/cloud/current/api.html#tag/Topic-(v3)/operation/listKafkaTopics">List Topics</a>
     */
    suspend fun listTopics(): List<TopicData>

    // TODO: Future implementations
    // suspend fun describeTopic(topicName: String): TopicDetails
    // suspend fun createTopic(request: CreateTopicRequest): TopicData
    // suspend fun deleteTopic(topicName: String)
    // suspend fun getTopicConfig(topicName: String): Map<String, String>
    // suspend fun updateTopicConfig(topicName: String, config: Map<String, String>)

    // Partitions

    // TODO: Future implementations
    // suspend fun listTopicPartitions(topicName: String): List<PartitionData>
    // suspend fun describeTopicPartition(topicName: String, partitionId: Int): PartitionDetails

    // Consumer Groups

    // TODO: Future implementations
    // suspend fun listConsumerGroups(): List<ConsumerGroupData>
    // suspend fun describeConsumerGroup(groupId: String): ConsumerGroupDetails
}
