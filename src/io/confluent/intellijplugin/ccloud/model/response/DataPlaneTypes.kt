package io.confluent.intellijplugin.ccloud.model.response

import io.confluent.intellijplugin.ccloud.client.CCloudRestClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Placeholder types for data plane operations (to be implemented).
 * this file will be deleted when the data plane operations are implemented.
 */

@Serializable
data class CreateTopicRequest(
    @SerialName("topic_name") val topicName: String,
    @SerialName("partitions_count") val partitionsCount: Int,
    @SerialName("replication_factor") val replicationFactor: Int? = null,
    @SerialName("configs") val configs: List<ConfigEntry>? = null
) {
    @Serializable
    data class ConfigEntry(
        @SerialName("name") val name: String,
        @SerialName("value") val value: String
    )
}

data class TopicDetails(val topicName: String)
data class PartitionData(val partitionId: Int)

data class ProduceRequest(val value: String)
data class ProduceResponse(val success: Boolean)
data class ConsumeRequest(val maxRecords: Int)
data class ConsumerRecord(val key: String?, val value: String)

data class ConsumerGroupData(val groupId: String, val state: String)
data class ConsumerGroupDetails(val groupId: String)

@Serializable
data class TopicOffsetsResponse(
    @SerialName("total_records") val totalRecords: Long
)
