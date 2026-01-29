package io.confluent.intellijplugin.ccloud.fetcher

import io.confluent.intellijplugin.ccloud.client.CCloudApiException
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

    suspend fun getPartitionOffsets(topicName: String, partitionId: Int, fromBeginning: Boolean = false): PartitionOffsets {
        val path = "/kafka/v3/clusters/$clusterId/internal/topics/$topicName/partitions/$partitionId/records:offsets?from_beginning=$fromBeginning"
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

    override suspend fun getTopicMessageCount(topicName: String): Long {
        val path = "/kafka/v3/clusters/$clusterId/internal/topics/$topicName/partitions/-/records:offsets"
        return kafkaClient.fetch(path) { body ->
            val response = json.decodeFromString<TopicOffsetsResponse>(body)
            response.totalRecords
        }
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
                        // Use API-provided schemaType, fall back to inference if null
                        val schemaType = latestSchema.schemaType ?: inferEmptySchemaType(latestSchema.schema)
                        SubjectData(
                            name = subject,
                            latestVersion = latestSchema.version,
                            schemaType = schemaType,
                            compatibility = null
                        )
                    } catch (e: Exception) {
                        thisLogger().warn("Failed to fetch details for subject '$subject': ${e.message}", e)
                        // Return basic info even on error to ensure schema is still displayed
                        SubjectData(
                            name = subject,
                            latestVersion = null,
                            schemaType = null,
                            compatibility = null
                        )
                    }
                }
            }.awaitAll()
        }
    }

    /**
     * Infer schema type from content when API doesn't provide schemaType.
     * Used as fallback for schemas where schemaType field is null/missing.
     */
    private fun inferEmptySchemaType(schema: String): String {
        val trimmed = schema.trim()
        return when {
            trimmed.startsWith("{}") -> "JSON"
            trimmed.contains("syntax") && trimmed.contains("proto") -> "PROTOBUF"
            else -> "AVRO"
        }
    }

    override suspend fun listSubjectVersions(subject: String): List<Int> {
        requireSchemaRegistry()
        val path = String.format(CloudConfig.DataPlane.SchemaRegistry.SUBJECT_VERSIONS_URI, encodeSubject(subject))
        return schemaRegistryClient!!.fetch(path) { body ->
            json.decodeFromString(ListSerializer(Int.serializer()), body)
        }
    }

    override suspend fun getSchemaByVersion(subject: String, version: String): SchemaVersionResponse {
        requireSchemaRegistry()
        val path = String.format(CloudConfig.DataPlane.SchemaRegistry.SUBJECT_VERSION_URI, encodeSubject(subject), version)
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

    override suspend fun registerSchema(
        subject: String,
        schema: String,
        schemaType: String,
        references: List<SchemaReference>
    ): Int {
        requireSchemaRegistry()
        val path = String.format(CloudConfig.DataPlane.SchemaRegistry.REGISTER_SCHEMA_URI, encodeSubject(subject))
        val request = RegisterSchemaRequest(schema, schemaType, references)
        val requestBody = json.encodeToString(RegisterSchemaRequest.serializer(), request)
        val responseBody = schemaRegistryClient!!.executeRequest(path, "POST", requestBody)
        val response = json.decodeFromString<RegisterSchemaResponse>(responseBody)
        return response.id
    }

    override suspend fun checkSchemaExists(
        subject: String,
        schema: String,
        schemaType: String
    ): SchemaVersionResponse? {
        requireSchemaRegistry()
        val path = String.format(CloudConfig.DataPlane.SchemaRegistry.CHECK_SCHEMA_URI, encodeSubject(subject))
        val request = RegisterSchemaRequest(schema, schemaType)
        val requestBody = json.encodeToString(RegisterSchemaRequest.serializer(), request)

        return try {
            val responseBody = schemaRegistryClient!!.executeRequest(path, "POST", requestBody)
            json.decodeFromString<SchemaVersionResponse>(responseBody)
        } catch (e: CCloudApiException) {
            // 404 means schema doesn't exist
            if (e.statusCode == 404) null else throw e
        }
    }

    override suspend fun deleteSubject(subject: String, permanent: Boolean): List<Int> {
        requireSchemaRegistry()
        val path = String.format(CloudConfig.DataPlane.SchemaRegistry.DELETE_SUBJECT_URI, encodeSubject(subject))
        val uri = if (permanent) "$path?permanent=true" else path
        val responseBody = schemaRegistryClient!!.executeRequest(uri, "DELETE")
        return json.decodeFromString<DeleteSubjectResponse>(responseBody)
    }

    override suspend fun deleteSchemaVersion(
        subject: String,
        version: String,
        permanent: Boolean
    ): Int {
        requireSchemaRegistry()
        val path = String.format(CloudConfig.DataPlane.SchemaRegistry.DELETE_VERSION_URI, encodeSubject(subject), version)
        val uri = if (permanent) "$path?permanent=true" else path
        val responseBody = schemaRegistryClient!!.executeRequest(uri, "DELETE")
        return json.decodeFromString<DeleteVersionResponse>(responseBody)
    }

    /**
     * Get compatibility level for a subject.
     *
     * @return Compatibility level or null if subject uses global default
     */
    override suspend fun getSubjectCompatibility(subject: String): String? {
        requireSchemaRegistry()
        val path = String.format(CloudConfig.DataPlane.SchemaRegistry.SUBJECT_CONFIG_URI, encodeSubject(subject))

        return try {
            schemaRegistryClient!!.fetch(path) { body ->
                val response = json.decodeFromString<CompatibilityConfigResponse>(body)
                response.level
            }
        } catch (e: CCloudApiException) {
            // 404 means subject uses global default
            if (e.statusCode == 404) null else throw e
        }
    }

    override suspend fun updateSubjectCompatibility(subject: String, compatibility: String): String {
        requireSchemaRegistry()
        val path = String.format(CloudConfig.DataPlane.SchemaRegistry.SUBJECT_CONFIG_URI, encodeSubject(subject))
        val request = UpdateCompatibilityRequest(compatibility)
        val requestBody = json.encodeToString(UpdateCompatibilityRequest.serializer(), request)
        val responseBody = schemaRegistryClient!!.executeRequest(path, "PUT", requestBody)
        val response = json.decodeFromString<CompatibilityConfigResponse>(responseBody)
        return response.level ?: compatibility
    }

    override suspend fun getGlobalCompatibility(): String? {
        requireSchemaRegistry()
        val path = CloudConfig.DataPlane.SchemaRegistry.CONFIG_URI

        return try {
            schemaRegistryClient!!.fetch(path) { body ->
                val response = json.decodeFromString<CompatibilityConfigResponse>(body)
                response.level
            }
        } catch (e: CCloudApiException) {
            thisLogger().warn("Failed to get global compatibility: ${e.message}")
            null
        }
    }

    /**
     * URL-encode subject name to handle spaces and special characters.
     * Replaces '+' with '%20' for proper URL path encoding (not form encoding).
     */
    private fun encodeSubject(subject: String): String {
        return URLEncoder.encode(subject, StandardCharsets.UTF_8).replace("+", "%20")
    }

    private fun requireSchemaRegistry() {
        if (schemaRegistryClient == null || schemaRegistryId == null) {
            throw IllegalStateException("Schema Registry unavailable for cluster $clusterId")
        }
    }
}
