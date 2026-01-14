package io.confluent.intellijplugin.ccloud.fetcher

import io.confluent.intellijplugin.ccloud.client.DataPlaneRestClient
import io.confluent.intellijplugin.ccloud.config.CloudConfig
import io.confluent.intellijplugin.ccloud.model.response.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Data plane fetcher implementation for Kafka REST API v3 and Schema Registry API v1.
 *
 * @param kafkaClient REST client for Kafka operations
 * @param schemaRegistryClient REST client for Schema Registry (null if unavailable)
 * @param clusterId Kafka cluster ID
 * @param schemaRegistryId Schema Registry ID (null if unavailable)
 */
class DataPlaneFetcherImpl(
    private val kafkaClient: DataPlaneRestClient,
    private val schemaRegistryClient: DataPlaneRestClient?,
    private val clusterId: String,
    private val schemaRegistryId: String?
) : DataPlaneFetcher {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun listTopics(): List<TopicData> {
        val path = String.format(CloudConfig.DataPlane.Kafka.TOPICS_URI, clusterId)
        return kafkaClient.fetchList(path) { body ->
            val response = json.decodeFromString<ListTopicsResponse>(body)
            response.data to response.metadata.next
        }
    }

    override suspend fun createTopic(request: CreateTopicRequest): TopicData {
        TODO("Implement createTopic")
    }

    override suspend fun deleteTopic(topicName: String) {
        TODO("Implement deleteTopic")
    }

    override suspend fun describeTopic(topicName: String): TopicDetails {
        TODO("Implement describeTopic")
    }

    override suspend fun describeTopicPartitions(topicName: String): List<PartitionData> {
        TODO("Implement describeTopicPartitions")
    }

    override suspend fun getTopicPartitions(topicName: String): List<PartitionInfo> {
        val path = "/kafka/v3/clusters/$clusterId/topics/$topicName/partitions"
        return kafkaClient.fetchList(path) { body ->
            val response = json.decodeFromString<ListPartitionsResponse>(body)
            response.data to response.metadata.next
        }
    }

    override suspend fun getPartitionReplicas(topicName: String, partitionId: Int): List<ReplicaDetails> {
        val path = "/kafka/v3/clusters/$clusterId/topics/$topicName/partitions/$partitionId/replicas"
        return kafkaClient.fetchList(path) { body ->
            val response = json.decodeFromString<ListReplicasResponse>(body)
            response.data to response.metadata.next
        }
    }

    override suspend fun getTopicMessageCount(topicName: String): Long {
        val path = "/kafka/v3/clusters/$clusterId/internal/topics/$topicName/partitions/-/records:offsets"
        return kafkaClient.fetch(path) { body ->
            val response = json.decodeFromString<TopicOffsetsResponse>(body)
            response.totalRecords
        }
    }

    override suspend fun describeTopicConfiguration(topicName: String): Map<String, String> {
        TODO("Implement describeTopicConfiguration")
    }

    override suspend fun produceRecord(topicName: String, request: ProduceRequest): ProduceResponse {
        TODO("Implement produceRecord")
    }

    override suspend fun consumeRecords(topicName: String, request: ConsumeRequest): List<ConsumerRecord> {
        TODO("Implement consumeRecords")
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

        // Fetch details for all subjects in parallel (avoids N+1 sequential API calls)
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
                    } catch (_: Exception) {
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

    private fun requireSchemaRegistry() {
        if (schemaRegistryClient == null || schemaRegistryId == null) {
            throw IllegalStateException("Schema Registry unavailable for cluster $clusterId")
        }
    }
}
