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

    /** Get all consumer groups (aligns with Kafka KafkaClient.getConsumerGroups). */
    suspend fun getConsumerGroups(): List<ConsumerGroupData>

    /** Get consumer group details (topics, partitions, lag, offset). */
    suspend fun describeConsumerGroup(groupId: String): ConsumerGroupDetails

    /** Get all schema subjects (aligns with ConfluentRegistryClient.getAllSubjects, returns raw subject names). */
    suspend fun getAllSubjects(): List<String>

    /** Load enriched schema info for a single schema (matches Kafka ConfluentRegistryClient). */
    suspend fun loadSchemaInfo(schemaName: String): SchemaData

    /** List all versions for a schema. */
    suspend fun listSchemaVersions(schemaName: String): List<Int>

    /** Get schema version info for a specific version ("latest" or version number). */
    suspend fun getSchemaVersionInfo(schemaName: String, version: String): SchemaVersionResponse

    /** Get latest schema version info (convenience method, matches Kafka). */
    suspend fun getLatestVersionInfo(schemaName: String): SchemaVersionResponse

    /** Get schema by global ID. */
    suspend fun getSchemaIdInfo(schemaId: Int): SchemaByIdResponse

    /** Get total message count for a topic (across all partitions). */
    suspend fun getTopicMessageCount(topicName: String): Long

    /** Get beginning offsets for all partitions of a topic. Returns partition ID to offset. */
    suspend fun getTopicBeginningOffsets(topicName: String): Map<Int, Long>

    /** Get end offsets for all partitions of a topic. Returns partition ID to offset. */
    suspend fun getTopicEndOffsets(topicName: String): Map<Int, Long>
}
