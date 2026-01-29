package io.confluent.intellijplugin.ccloud.fetcher

import io.confluent.intellijplugin.ccloud.model.response.*

/**
 * Data plane operations for Confluent Cloud.
 */
interface DataPlaneFetcher {

    /** List all topics. */
    suspend fun listTopics(): List<TopicData>

    /** Create a topic. */
    suspend fun createTopic(request: CreateTopicRequest): TopicData

    /** Delete a topic. */
    suspend fun deleteTopic(topicName: String)

    /** Get partition details (ID, message count, offset, leader, replicas). */
    suspend fun describeTopicPartitions(topicName: String): List<PartitionData>

    /** Get topic configuration. */
    suspend fun describeTopicConfiguration(topicName: String): List<ConfigData>

    /** Get total message count for a topic (across all partitions). */
    suspend fun getTopicMessageCount(topicName: String): Long

    /** List all consumer groups. */
    suspend fun listConsumerGroups(): List<ConsumerGroupData>

    /** Get consumer group details (topics, partitions, lag, offset). */
    suspend fun describeConsumerGroup(groupId: String): ConsumerGroupDetails

    /** List all subjects (throws if Schema Registry unavailable). */
    suspend fun listSubjects(): List<String>

    /** List subjects with metadata (version, type, etc). */
    suspend fun listSubjectsWithDetails(): List<SubjectData>

    /** List all versions for a subject. */
    suspend fun listSubjectVersions(subject: String): List<Int>

    /** Get schema by version ("latest" or version number). */
    suspend fun getSchemaByVersion(subject: String, version: String): SchemaVersionResponse

    /** Get schema by global ID. */
    suspend fun getSchemaById(schemaId: Int): SchemaByIdResponse

    /** Check if a schema already exists. Returns null if not found. */
    suspend fun checkSchemaExists(
        subject: String,
        schema: String,
        schemaType: String = "AVRO"
    ): SchemaVersionResponse?

    /** Register a new schema version for a subject. Returns the schema ID. */
    suspend fun registerSchema(
        subject: String,
        schema: String,
        schemaType: String = "AVRO",
        references: List<SchemaReference> = emptyList()
    ): Int

    /** Delete a specific schema version. Returns the deleted version number. */
    suspend fun deleteSchemaVersion(subject: String, version: String, permanent: Boolean = false): Int

    /** Delete all versions of a subject. Returns list of deleted version numbers. */
    suspend fun deleteSubject(subject: String, permanent: Boolean = false): List<Int>

    /** Get compatibility level for a subject. Returns null if using global default. */
    suspend fun getSubjectCompatibility(subject: String): String?

    /** Update compatibility level for a subject. Returns the new compatibility level. */
    suspend fun updateSubjectCompatibility(subject: String, compatibility: String): String

    /** Get global default compatibility level. */
    suspend fun getGlobalCompatibility(): String?
}
