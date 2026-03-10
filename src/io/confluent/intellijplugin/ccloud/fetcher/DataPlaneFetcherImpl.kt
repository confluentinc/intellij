package io.confluent.intellijplugin.ccloud.fetcher

import com.intellij.openapi.diagnostic.thisLogger
import io.confluent.intellijplugin.ccloud.client.CCloudRestClient
import io.confluent.intellijplugin.ccloud.config.CloudConfig
import io.confluent.intellijplugin.ccloud.model.response.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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

    override suspend fun getTopics(): List<TopicData> {
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

    override suspend fun getTopicConfig(topicName: String): List<ConfigData> {
        val path = String.format(CloudConfig.DataPlane.Kafka.TOPIC_CONFIGS_URI, clusterId, topicName)
        return kafkaClient.fetchList(path) { body ->
            val response = json.decodeFromString<ListConfigsResponse>(body)
            response.data to response.metadata.next
        }
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

    override suspend fun getAllSubjects(): List<String> {
        requireSchemaRegistry()
        val path = CloudConfig.DataPlane.SchemaRegistry.SUBJECTS_URI
        return schemaRegistryClient!!.fetch(path) { body ->
            json.decodeFromString(ListSerializer(String.serializer()), body)
        }
    }

    override suspend fun loadSchemaInfo(schemaName: String): SchemaData {
        requireSchemaRegistry()
        return try {
            val latestSchema = getLatestVersionInfo(schemaName)

            SchemaData(
                name = schemaName,
                latestVersion = latestSchema.version,
                schemaType = latestSchema.schemaType ?: "AVRO",
                compatibility = null  // Fetched on-demand to reduce API calls
            )
        } catch (e: Exception) {
            // Cancellations are expected when user clicks refresh - use debug level
            if (e is kotlinx.coroutines.CancellationException) {
                thisLogger().debug("Cancelled fetch schema info for '$schemaName': ${e.message}")
            } else {
                thisLogger().warn("Failed to fetch schema info for '$schemaName'", e)
            }
            SchemaData(name = schemaName)
        }
    }

    override suspend fun listSchemaVersions(schemaName: String): List<Long> {
        requireSchemaRegistry()
        val encodedSubject = encodeUrlPathSegment(schemaName)
        val path = String.format(CloudConfig.DataPlane.SchemaRegistry.SUBJECT_VERSIONS_URI, encodedSubject)
        return schemaRegistryClient!!.fetch(path) { body ->
            json.decodeFromString(ListSerializer(Int.serializer()), body).map { it.toLong() }
        }
    }

    override suspend fun getSchemaVersionInfo(schemaName: String, version: Long): SchemaVersionResponse {
        requireSchemaRegistry()
        val encodedSubject = encodeUrlPathSegment(schemaName)
        val path =
            String.format(CloudConfig.DataPlane.SchemaRegistry.SUBJECT_VERSION_URI, encodedSubject, version.toString())
        return schemaRegistryClient!!.fetch(path) { body ->
            json.decodeFromString<SchemaVersionResponse>(body)
        }
    }

    override suspend fun getLatestVersionInfo(schemaName: String): SchemaVersionResponse {
        requireSchemaRegistry()
        val encodedSubject = encodeUrlPathSegment(schemaName)
        val path = String.format(CloudConfig.DataPlane.SchemaRegistry.SUBJECT_VERSION_URI, encodedSubject, "latest")
        return schemaRegistryClient!!.fetch(path) { body ->
            json.decodeFromString<SchemaVersionResponse>(body)
        }
    }

    override suspend fun getSchemaIdInfo(schemaId: Int): SchemaByIdResponse {
        requireSchemaRegistry()
        val path = String.format(CloudConfig.DataPlane.SchemaRegistry.SCHEMA_BY_ID_URI, schemaId)
        return schemaRegistryClient!!.fetch(path) { body ->
            json.decodeFromString<SchemaByIdResponse>(body)
        }
    }

    override suspend fun getSchemaByGuid(guid: String): SchemaByIdResponse {
        requireSchemaRegistry()
        val path = String.format(CloudConfig.DataPlane.SchemaRegistry.SCHEMA_BY_GUID_URI, guid)
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

    override suspend fun getTopicBeginningOffsets(topicName: String): Map<Int, Long> =
        fetchOffsetsForAllPartitions(topicName, fromBeginning = true)

    override suspend fun getTopicEndOffsets(topicName: String): Map<Int, Long> =
        fetchOffsetsForAllPartitions(topicName, fromBeginning = false)

    private suspend fun fetchOffsetsForAllPartitions(
        topicName: String,
        fromBeginning: Boolean
    ): Map<Int, Long> {
        val partitions = describeTopicPartitions(topicName)
        return coroutineScope {
            partitions.map { partition ->
                async {
                    partition.partitionId to getPartitionOffsets(
                        topicName, partition.partitionId, fromBeginning
                    ).nextOffset
                }
            }.awaitAll().toMap()
        }
    }

    // Schema write operations

    override suspend fun createSchema(
        schemaName: String,
        request: RegisterSchemaRequest
    ): RegisterSchemaResponse {
        requireSchemaRegistry()
        val encodedSubject = encodeUrlPathSegment(schemaName)
        val path = String.format(CloudConfig.DataPlane.SchemaRegistry.REGISTER_SCHEMA_URI, encodedSubject)
        val requestBody = json.encodeToString(RegisterSchemaRequest.serializer(), request)
        val responseBody = schemaRegistryClient!!.executeRequest(path, "POST", requestBody)
        return json.decodeFromString<RegisterSchemaResponse>(responseBody)
    }

    override suspend fun checkSchemaExists(
        schemaName: String,
        request: RegisterSchemaRequest
    ): CheckSchemaExistsResponse {
        requireSchemaRegistry()
        val encodedSubject = encodeUrlPathSegment(schemaName)
        val path = String.format(CloudConfig.DataPlane.SchemaRegistry.CHECK_SCHEMA_URI, encodedSubject)
        val requestBody = json.encodeToString(RegisterSchemaRequest.serializer(), request)
        val responseBody = schemaRegistryClient!!.executeRequest(path, "POST", requestBody)
        return json.decodeFromString<CheckSchemaExistsResponse>(responseBody)
    }

    override suspend fun deleteSchema(schemaName: String, permanent: Boolean): DeleteSubjectResponse {
        requireSchemaRegistry()
        val encodedSubject = encodeUrlPathSegment(schemaName)
        val path = String.format(CloudConfig.DataPlane.SchemaRegistry.DELETE_SUBJECT_URI, encodedSubject)
        val pathWithQuery = if (permanent) "$path?permanent=true" else path
        val responseBody = schemaRegistryClient!!.executeRequest(pathWithQuery, "DELETE")
        return json.decodeFromString<DeleteSubjectResponse>(responseBody)
    }

    override suspend fun deleteSchemaVersion(
        schemaName: String,
        version: Long,
        permanent: Boolean
    ): DeleteSchemaVersionResponse {
        requireSchemaRegistry()
        val encodedSubject = encodeUrlPathSegment(schemaName)
        val path = String.format(
            CloudConfig.DataPlane.SchemaRegistry.DELETE_VERSION_URI,
            encodedSubject,
            version.toString()
        )
        val pathWithQuery = if (permanent) "$path?permanent=true" else path
        val responseBody = schemaRegistryClient!!.executeRequest(pathWithQuery, "DELETE")
        return json.decodeFromString<DeleteSchemaVersionResponse>(responseBody)
    }

    override suspend fun getSchemaCompatibility(schemaName: String): CompatibilityResponse {
        requireSchemaRegistry()
        val encodedSubject = encodeUrlPathSegment(schemaName)
        val path = String.format(CloudConfig.DataPlane.SchemaRegistry.GET_SUBJECT_COMPATIBILITY_URI, encodedSubject)
        val responseBody = schemaRegistryClient!!.executeRequest(path, "GET")
        return json.decodeFromString<CompatibilityResponse>(responseBody)
    }


    private fun requireSchemaRegistry() {
        if (schemaRegistryClient == null || schemaRegistryId == null) {
            throw IllegalStateException("Schema Registry unavailable for cluster $clusterId")
        }
    }

    /**
     * Encode subject name for URL path (not query params).
     * URLEncoder.encode() is for form data (spaces → +), but URL paths need spaces → %20.
     */
    private fun encodeUrlPathSegment(segment: String): String {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8.toString())
            .replace("+", "%20")  // Fix form encoding to path encoding
    }
}
