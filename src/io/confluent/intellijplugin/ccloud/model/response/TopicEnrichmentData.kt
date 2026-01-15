package io.confluent.intellijplugin.ccloud.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response from GET /kafka/v3/clusters/{cluster_id}/topics/{topic_name}/partitions
 * Contains detailed partition information including ISR (In Sync Replicas).
 */
@Serializable
data class ListPartitionsResponse(
    @SerialName("kind") val kind: String = "KafkaPartitionList",
    @SerialName("metadata") val metadata: io.confluent.intellijplugin.ccloud.client.ControlPlaneRestClient.ListMetadata,
    @SerialName("data") val data: List<PartitionInfo>
)

/**
 * Detailed partition information from Kafka REST API v3.
 */
@Serializable
data class PartitionInfo(
    @SerialName("cluster_id") val clusterId: String,
    @SerialName("topic_name") val topicName: String,
    @SerialName("partition_id") val partitionId: Int,
    @SerialName("leader") val leader: ReplicaReference? = null,
    @SerialName("replicas") val replicas: ReplicaList? = null,
    @SerialName("isr") val isr: ReplicaList? = null  // In Sync Replicas
)

/**
 * Replica list wrapper from Kafka REST API v3.
 */
@Serializable
data class ReplicaList(
    @SerialName("data") val data: List<ReplicaReference> = emptyList()
)

/**
 * Replica reference (either broker_id or related link).
 * Confluent Cloud API returns "related" links instead of broker IDs.
 */
@Serializable
data class ReplicaReference(
    @SerialName("broker_id") val brokerId: Int? = null,
    @SerialName("related") val related: String? = null
)

/**
 * Response from GET /kafka/v3/clusters/{cluster_id}/topics/{topic_name}/partitions/{partition_id}/replicas
 * Contains replica details including in_sync status.
 */
@Serializable
data class ListReplicasResponse(
    @SerialName("kind") val kind: String = "KafkaReplicaList",
    @SerialName("metadata") val metadata: io.confluent.intellijplugin.ccloud.client.ControlPlaneRestClient.ListMetadata,
    @SerialName("data") val data: List<ReplicaDetails>
)

/**
 * Detailed replica information including in_sync status.
 */
@Serializable
data class ReplicaDetails(
    @SerialName("cluster_id") val clusterId: String,
    @SerialName("topic_name") val topicName: String,
    @SerialName("partition_id") val partitionId: Int,
    @SerialName("broker_id") val brokerId: Int,
    @SerialName("leader") val leader: Boolean = false,
    @SerialName("in_sync") val inSync: Boolean = false  // ← Key field for ISR!
)

/**
 * Response from GET /v3/clusters/{cluster_id}/internal/topics/{topic_name}/partitions/-/records:offsets
 * Contains total message count across all partitions.
 */
@Serializable
data class TopicOffsetsResponse(
    @SerialName("cluster_id") val clusterId: String,
    @SerialName("topic_name") val topicName: String,
    @SerialName("partition_data_list") val partitionDataList: List<PartitionOffsetData> = emptyList(),
    @SerialName("total_records") val totalRecords: Long  // Total messages across all partitions
)

/**
 * Per-partition offset data.
 */
@Serializable
data class PartitionOffsetData(
    @SerialName("partition_id") val partitionId: Int,
    @SerialName("next_offset") val nextOffset: Long
)
