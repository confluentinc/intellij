package io.confluent.intellijplugin.ccloud.fetcher

import io.confluent.intellijplugin.ccloud.model.response.*

/** Data plane operations for Confluent Cloud. */
interface DataPlaneFetcher {

    suspend fun getTopics(): List<TopicData>
    suspend fun createTopic(request: CreateTopicRequest): TopicData
    suspend fun deleteTopic(topicName: String)
    suspend fun describeTopicPartitions(topicName: String): List<PartitionData>
    suspend fun getTopicConfig(topicName: String): List<ConfigData>
    suspend fun produceRecord(topicName: String, request: ProduceRequest): ProduceResponse
    suspend fun consumeRecords(topicName: String, request: ConsumeRecordsRequest): ConsumeRecordsResponse
    suspend fun getConsumerGroups(): List<ConsumerGroupData>
    suspend fun describeConsumerGroup(groupId: String): ConsumerGroupDetails

    /** Get all subject names from Schema Registry. */
    suspend fun getAllSubjects(): List<String>

    /** Load schema info (version, type, compatibility) for a subject. */
    suspend fun loadSchemaInfo(subjectName: String): SchemaData

    /** List all versions for a subject. */
    suspend fun listSchemaVersions(subjectName: String): List<Long>

    /** Get schema content for a specific version. */
    suspend fun getSchemaVersionInfo(subjectName: String, version: Long): SchemaVersionResponse

    /** Get latest schema version. */
    suspend fun getLatestVersionInfo(subjectName: String): SchemaVersionResponse

    /** Get schema by global ID. */
    suspend fun getSchemaIdInfo(schemaId: Int): SchemaByIdResponse

    suspend fun getTopicMessageCount(topicName: String): Long
    suspend fun getTopicBeginningOffsets(topicName: String): Map<Int, Long>
    suspend fun getTopicEndOffsets(topicName: String): Map<Int, Long>
}
