package io.confluent.intellijplugin.ccloud.model.response

import io.confluent.intellijplugin.ccloud.client.CCloudRestClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

@Serializable
data class TopicOffsetsResponse(
    @SerialName("total_records") val totalRecords: Long
)

// Partition data
@Serializable
data class ListPartitionsResponse(
    @SerialName("data") val data: List<PartitionData>,
    @SerialName("metadata") val metadata: CCloudRestClient.ListMetadata
)

@Serializable
data class PartitionData(
    @SerialName("partition_id") val partitionId: Int,
    @SerialName("leader") val leader: BrokerRelationship? = null,
    @SerialName("replicas") val replicas: BrokerRelationship? = null,
    @SerialName("isr") val isr: BrokerRelationship? = null
)

@Serializable
data class BrokerRelationship(
    @SerialName("related") val related: String
)

@Serializable
data class PartitionOffsets(
    @SerialName("cluster_id") val clusterId: String,
    @SerialName("topic_name") val topicName: String,
    @SerialName("partition_id") val partitionId: Int,
    @SerialName("next_offset") val nextOffset: Long
)

/**
 * Offset information for a partition, including both beginning and end offsets.
 * Used for calculating start positions like LATEST_OFFSET_MINUS_X.
 */
data class PartitionOffsetInfo(
    val partitionId: Int,
    val beginningOffset: Long,
    val endOffset: Long
)

// Config data
@Serializable
data class ListConfigsResponse(
    @SerialName("data") val data: List<ConfigData>,
    @SerialName("metadata") val metadata: CCloudRestClient.ListMetadata
)

@Serializable
data class ConfigData(
    @SerialName("name") val name: String,
    @SerialName("value") val value: String?,
    @SerialName("is_default") val isDefault: Boolean,
    @SerialName("synonyms") val synonyms: List<ConfigSynonym>? = null
)

@Serializable
data class ConfigSynonym(
    @SerialName("name") val name: String,
    @SerialName("value") val value: String?,
    @SerialName("source") val source: String
)

// Placeholder types for future implementation
data class ProduceRequest(val value: String)
data class ProduceResponse(val success: Boolean)
data class ConsumeRequest(val maxRecords: Int)
data class ConsumerRecord(val key: String?, val value: String)
data class ConsumerGroupData(val groupId: String, val state: String)
data class ConsumerGroupDetails(val groupId: String)
