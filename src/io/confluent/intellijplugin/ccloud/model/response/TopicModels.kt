package io.confluent.intellijplugin.ccloud.model.response

import io.confluent.intellijplugin.ccloud.client.CCloudRestClient
import io.confluent.intellijplugin.model.TopicPresentable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Response from GET /kafka/v3/clusters/{cluster_id}/topics */
@Serializable
data class ListTopicsResponse(
    @SerialName("kind") val kind: String = "KafkaTopicList",
    @SerialName("metadata") val metadata: CCloudRestClient.ListMetadata,
    @SerialName("data") val data: List<TopicData>
)

/** Kafka topic resource from Confluent Cloud Data Plane API. */
@Serializable
data class TopicData(
    @SerialName("kind") val kind: String = "KafkaTopic",
    @SerialName("cluster_id") val clusterId: String,
    @SerialName("topic_name") val topicName: String,
    @SerialName("is_internal") val isInternal: Boolean = false,
    @SerialName("replication_factor") val replicationFactor: Int,
    @SerialName("partitions_count") val partitionsCount: Int
)

/** Enrichment data for topics (requires additional API calls beyond basic topic list). */
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

/** Converts TopicData (from REST API) to TopicPresentable (for UI). */
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

/** Converts list of TopicData to TopicPresentables without enrichment. */
fun List<TopicData>.toPresentable(): List<TopicPresentable> {
    return map { it.toPresentable() }
}

/** Converts list of TopicData to TopicPresentables with enrichment data. */
fun List<TopicData>.toPresentableWithEnrichment(enrichmentMap: Map<String, TopicEnrichmentData>): List<TopicPresentable> {
    return map { topicData ->
        topicData.toPresentable(enrichmentMap[topicData.topicName])
    }
}

/** Result of enriching a single topic with additional data. */
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
