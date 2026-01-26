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

    /** Produce a record to a topic. */
    suspend fun produceRecord(topicName: String, request: ProduceRequest): ProduceResponse

    suspend fun consumeRecords(topicName: String, request: ConsumeRecordsRequest): ConsumeRecordsResponse

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

    /** Get total message count for a topic (across all partitions). */
    suspend fun getTopicMessageCount(topicName: String): Long
}
