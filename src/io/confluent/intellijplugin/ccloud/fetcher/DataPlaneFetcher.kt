package io.confluent.intellijplugin.ccloud.fetcher

import io.confluent.intellijplugin.ccloud.model.response.*

/**
 * Data plane operations for Confluent Cloud.
 *
 * Covers Kafka REST API v3 and Schema Registry API v1.
 *
 * @see <a href="https://docs.confluent.io/cloud/current/api.html#tag/Topic-(v3)">Kafka REST API v3</a>
 * @see <a href="https://docs.confluent.io/cloud/current/api.html#tag/Schemas-(v1)">Schema Registry API v1</a>
 */
interface DataPlaneFetcher {

    /** List all topics. */
    suspend fun listTopics(): List<TopicData>

    /** Create a topic. */
    suspend fun createTopic(request: CreateTopicRequest): TopicData

    /** Delete a topic. */
    suspend fun deleteTopic(topicName: String)

    /** Get topic details (partitions, replication factor, messages, etc). */
    suspend fun describeTopic(topicName: String): TopicDetails

    /** Get partition details (ID, message count, offset, leader, replicas). */
    suspend fun describeTopicPartitions(topicName: String): List<PartitionData>

    /** Get partition details including ISR (In Sync Replicas) information. */
    suspend fun getTopicPartitions(topicName: String): List<PartitionInfo>

    /** Get replica details for a specific partition (includes in_sync status). */
    suspend fun getPartitionReplicas(topicName: String, partitionId: Int): List<ReplicaDetails>

    /** Get total message count for a topic across all partitions. */
    suspend fun getTopicMessageCount(topicName: String): Long

    /** Get topic configuration. */
    suspend fun describeTopicConfiguration(topicName: String): Map<String, String>

    /** Produce a record to a topic. */
    suspend fun produceRecord(topicName: String, request: ProduceRequest): ProduceResponse

    /** Consume records from a topic. */
    suspend fun consumeRecords(topicName: String, request: ConsumeRequest): List<ConsumerRecord>

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
}
