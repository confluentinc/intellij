package io.confluent.intellijplugin.ccloud.fetcher

import io.confluent.intellijplugin.ccloud.client.CCloudRestClient
import io.confluent.intellijplugin.ccloud.config.CloudConfig
import io.confluent.intellijplugin.ccloud.model.response.*
import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Data plane fetcher implementation for Confluent Cloud.
 *
 * @param kafkaClient REST client for Cluster operations
 * @param schemaRegistryClient REST client for Schema Registry (null if unavailable)
 * @param clusterId Kafka cluster ID
 * @param schemaRegistryId Schema Registry ID (null if unavailable)
 */
class DataPlaneFetcherImpl(
    private val kafkaClient: CCloudRestClient,
    private val schemaRegistryClient: CCloudRestClient?,
    private val clusterId: String,
    private val schemaRegistryId: String?
) : DataPlaneFetcher {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun listTopics(): List<TopicData> {
        val path = String.format(CloudConfig.DataPlane.Kafka.TOPICS_URI, clusterId)
        val topics = kafkaClient.fetchList(path) { body ->
            val response = json.decodeFromString<ListTopicsResponse>(body)
            response.data to response.metadata.next
        }

        // Filter out virtual topics (replicationFactor = 0)
        return topics.filter { it.replicationFactor > 0 }
    }

    override suspend fun createTopic(request: CreateTopicRequest): TopicData {
        val path = String.format(CloudConfig.DataPlane.Kafka.TOPICS_URI, clusterId)
        val requestBody = json.encodeToString(CreateTopicRequest.serializer(), request)
        val responseBody = kafkaClient.executeRequest(path, "POST", requestBody)
        return json.decodeFromString<TopicData>(responseBody)
    }

    override suspend fun deleteTopic(topicName: String) {
        val path = String.format(CloudConfig.DataPlane.Kafka.TOPIC_URI, clusterId, topicName)
        kafkaClient.executeRequest(path, "DELETE")
    }

    override suspend fun describeTopicPartitions(topicName: String): List<PartitionData> {
        val path = String.format(CloudConfig.DataPlane.Kafka.PARTITIONS_URI, clusterId, topicName)
        return kafkaClient.fetchList(path) { body ->
            val response = json.decodeFromString<ListPartitionsResponse>(body)
            response.data to response.metadata.next
        }
    }

    suspend fun getPartitionOffsets(
        topicName: String,
        partitionId: Int,
        fromBeginning: Boolean = false
    ): PartitionOffsets {
        val path =
            "/kafka/v3/clusters/$clusterId/internal/topics/$topicName/partitions/$partitionId/records:offsets?from_beginning=$fromBeginning"
        return kafkaClient.fetch(path) { body ->
            json.decodeFromString<PartitionOffsets>(body)
        }
    }

    override suspend fun describeTopicConfiguration(topicName: String): List<ConfigData> {
        val path = String.format(CloudConfig.DataPlane.Kafka.TOPIC_CONFIGS_URI, clusterId, topicName)
        return kafkaClient.fetchList(path) { body ->
            val response = json.decodeFromString<ListConfigsResponse>(body)
            response.data to response.metadata.next
        }
    }

    override suspend fun produceRecord(topicName: String, request: ProduceRequest): ProduceResponse {
        TODO("Implement produceRecord")
    }

    override suspend fun consumeRecords(
        topicName: String,
        request: ConsumeRecordsRequest
    ): ConsumeRecordsResponse {
        val path = String.format(CloudConfig.DataPlane.Kafka.CCLOUD_SIMPLE_CONSUME_API_PATH, clusterId, topicName)
        val requestBody = json.encodeToString(request)
        val responseBody = kafkaClient.executeRequest(path, "POST", requestBody)
        return json.decodeFromString<ConsumeRecordsResponse>(responseBody)
    }

    override suspend fun listConsumerGroups(): List<ConsumerGroupData> {
        TODO("Implement listConsumerGroups")
    }

    override suspend fun describeConsumerGroup(groupId: String): ConsumerGroupDetails {
        TODO("Implement describeConsumerGroup")
    }

    override suspend fun listSubjects(): List<String> {
        requireSchemaRegistry()
        val path = CloudConfig.DataPlane.SchemaRegistry.SUBJECTS_URI
        return schemaRegistryClient!!.fetch(path) { body ->
            json.decodeFromString(ListSerializer(String.serializer()), body)
        }
    }

    override suspend fun listSubjectsWithDetails(): List<SubjectData> {
        requireSchemaRegistry()
        val subjects = listSubjects()

        // Fetch details for all subjects in parallel
        return coroutineScope {
            subjects.map { subject ->
                async {
                    try {
                        val latestSchema = getSchemaByVersion(subject, "latest")
                        SubjectData(
                            name = subject,
                            latestVersion = latestSchema.version,
                            schemaType = latestSchema.schemaType,
                            compatibility = null
                        )
                    } catch (e: Exception) {
                        thisLogger().warn("Failed to fetch details for subject '$subject': ${e.message}")
                        SubjectData(name = subject)
                    }
                }
            }.awaitAll()
        }
    }

    override suspend fun listSubjectVersions(subject: String): List<Int> {
        requireSchemaRegistry()
        val path = String.format(CloudConfig.DataPlane.SchemaRegistry.SUBJECT_VERSIONS_URI, subject)
        return schemaRegistryClient!!.fetch(path) { body ->
            json.decodeFromString(ListSerializer(Int.serializer()), body)
        }
    }

    override suspend fun getSchemaByVersion(subject: String, version: String): SchemaVersionResponse {
        requireSchemaRegistry()
        val path = String.format(CloudConfig.DataPlane.SchemaRegistry.SUBJECT_VERSION_URI, subject, version)
        return schemaRegistryClient!!.fetch(path) { body ->
            json.decodeFromString<SchemaVersionResponse>(body)
        }
    }

    override suspend fun getSchemaById(schemaId: Int): SchemaByIdResponse {
        requireSchemaRegistry()
        val path = String.format(CloudConfig.DataPlane.SchemaRegistry.SCHEMA_BY_ID_URI, schemaId)
        return schemaRegistryClient!!.fetch(path) { body ->
            json.decodeFromString<SchemaByIdResponse>(body)
        }
    }

    override suspend fun getTopicMessageCount(topicName: String): Long {
        val path = "/kafka/v3/clusters/$clusterId/internal/topics/$topicName/partitions/-/records:offsets"
        return kafkaClient.fetch(path) { body ->
            val response = json.decodeFromString<TopicOffsetsResponse>(body)
            response.totalRecords
        }
    }

    override suspend fun getTopicPartitionOffsets(topicName: String): Map<Int, PartitionOffsetInfo> {
        val partitions = describeTopicPartitions(topicName)

        // Fetch beginning and end offsets for each partition in parallel
        return coroutineScope {
            partitions.map { partition ->
                async {
                    val beginningOffset = getPartitionOffsets(topicName, partition.partitionId, fromBeginning = true).nextOffset
                    val endOffset = getPartitionOffsets(topicName, partition.partitionId, fromBeginning = false).nextOffset
                    partition.partitionId to PartitionOffsetInfo(
                        partitionId = partition.partitionId,
                        beginningOffset = beginningOffset,
                        endOffset = endOffset
                    )
                }
            }.awaitAll().toMap()
        }
    }

    private fun requireSchemaRegistry() {
        if (schemaRegistryClient == null || schemaRegistryId == null) {
            throw IllegalStateException("Schema Registry unavailable for cluster $clusterId")
        }
    }
}
