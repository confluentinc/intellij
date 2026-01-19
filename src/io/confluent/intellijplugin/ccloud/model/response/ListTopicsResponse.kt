package io.confluent.intellijplugin.ccloud.model.response

import io.confluent.intellijplugin.ccloud.client.CCloudRestClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response from GET /kafka/v3/clusters/{cluster_id}/topics
 *
 * @see <a href="https://docs.confluent.io/cloud/current/api.html#tag/Topic-(v3)/operation/listKafkaTopics">List Topics API</a>
 */
@Serializable
data class ListTopicsResponse(
    @SerialName("kind") val kind: String = "KafkaTopicList",
    @SerialName("metadata") val metadata: CCloudRestClient.ListMetadata,
    @SerialName("data") val data: List<TopicData>
)

/**
 * Kafka topic resource from Confluent Cloud Data Plane API.
 */
@Serializable
data class TopicData(
    @SerialName("kind") val kind: String = "KafkaTopic",
    @SerialName("cluster_id") val clusterId: String,
    @SerialName("topic_name") val topicName: String,
    @SerialName("is_internal") val isInternal: Boolean = false,
    @SerialName("replication_factor") val replicationFactor: Int,
    @SerialName("partitions_count") val partitionsCount: Int
)
