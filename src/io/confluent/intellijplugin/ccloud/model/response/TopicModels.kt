package io.confluent.intellijplugin.ccloud.model.response

import io.confluent.intellijplugin.ccloud.client.CCloudRestClient
import io.confluent.intellijplugin.model.TopicPresentable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Generic wrapper for paginated Confluent Cloud Data Plane API responses.
 * Made generic to avoid duplicating the same `kind`/`metadata`/`data` structure for each resource type
 * (topics, partitions, configs all share this pagination format).
 */
@Serializable
data class DataPlaneListResponse<T>(
    @SerialName("kind") val kind: String? = null,
    @SerialName("metadata") val metadata: CCloudRestClient.ListMetadata,
    @SerialName("data") val data: List<T>
)

interface DataPlaneResource {
    val kind: String
}

typealias ListTopicsResponse = DataPlaneListResponse<TopicData>

@Serializable
data class TopicData(
    @SerialName("kind") override val kind: String = "KafkaTopic",
    @SerialName("cluster_id") val clusterId: String,
    @SerialName("topic_name") val topicName: String,
    @SerialName("is_internal") val isInternal: Boolean = false,
    @SerialName("replication_factor") val replicationFactor: Int,
    @SerialName("partitions_count") val partitionsCount: Int
) : DataPlaneResource

/** Additional topic data from separate API calls. */
data class TopicEnrichmentData(
    val messageCount: Long? = null
)

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

/** Response from GET /kafka/v3/clusters/{cluster_id}/topics/{topic_name}/partitions */
typealias ListPartitionsResponse = DataPlaneListResponse<PartitionData>

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

/** Response from GET /kafka/v3/clusters/{cluster_id}/topics/{topic_name}/configs */
typealias ListConfigsResponse = DataPlaneListResponse<ConfigData>

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

fun TopicData.toPresentable(enrichmentData: TopicEnrichmentData? = null): TopicPresentable {
    return TopicPresentable(
        name = topicName,
        internal = isInternal,
        partitionList = emptyList(),
        partitions = partitionsCount,
        replicationFactor = replicationFactor,
        underReplicatedPartitions = null,
        noLeaders = null,
        messageCount = enrichmentData?.messageCount,
        isFavorite = false
    )
}

fun List<TopicData>.toPresentable(): List<TopicPresentable> {
    return map { it.toPresentable() }
}

fun List<TopicData>.toPresentableWithEnrichment(enrichmentMap: Map<String, TopicEnrichmentData>): List<TopicPresentable> {
    return map { topicData ->
        topicData.toPresentable(enrichmentMap[topicData.topicName])
    }
}

sealed class TopicEnrichmentResult {
    abstract val topicName: String
    abstract val progress: Pair<Int, Int>

    data class Success(
        override val topicName: String,
        val data: TopicEnrichmentData,
        override val progress: Pair<Int, Int>
    ) : TopicEnrichmentResult()

    data class Failure(
        override val topicName: String,
        override val progress: Pair<Int, Int>,
        val error: Exception
    ) : TopicEnrichmentResult()
}
