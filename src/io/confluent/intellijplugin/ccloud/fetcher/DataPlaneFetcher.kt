package io.confluent.intellijplugin.ccloud.fetcher

import io.confluent.intellijplugin.ccloud.model.response.*

/** Data plane operations for Confluent Cloud. */
interface DataPlaneFetcher {

    suspend fun getTopics(): List<TopicData>
    suspend fun createTopic(request: CreateTopicRequest): TopicData
    suspend fun deleteTopic(topicName: String)
    suspend fun describeTopicPartitions(topicName: String): List<PartitionData>
    suspend fun getTopicConfig(topicName: String): List<ConfigData>
    suspend fun getTopicMessageCount(topicName: String): Long
    suspend fun getTopicBeginningOffsets(topicName: String): Map<Int, Long>
    suspend fun getTopicEndOffsets(topicName: String): Map<Int, Long>

    /** Get beginning or end offset for a single partition. */
    suspend fun getPartitionOffset(topicName: String, partitionId: Int, fromBeginning: Boolean): Long

    suspend fun consumeRecords(topicName: String, request: ConsumeRecordsRequest): ConsumeRecordsResponse

    /**
     * Consume records from a single partition using the GET endpoint.
     * Supports timestamp-based seeking which the multi-partition POST endpoint does not.
     */
    suspend fun consumePartitionRecords(
        topicName: String,
        partitionId: Int,
        timestamp: Long? = null,
        offset: Long? = null,
        maxPollRecords: Int? = null
    ): PartitionConsumeData

    /** Produce a single record to a topic via the REST API v3. */
    suspend fun produceRecord(topicName: String, request: ProduceRecordRequest): ProduceRecordResponse

    /** Get all subject names from Schema Registry. */
    suspend fun getAllSubjects(): List<String>

    /** Load schema info (version, type) for a subject. */
    suspend fun loadSchemaInfo(subjectName: String): SchemaData

    /** List all versions for a subject. */
    suspend fun listSchemaVersions(subjectName: String): List<Long>

    /** Get schema content for a specific version. */
    suspend fun getSchemaVersionInfo(subjectName: String, version: Long): SchemaVersionResponse

    /** Get latest schema version. */
    suspend fun getLatestVersionInfo(subjectName: String): SchemaVersionResponse

    /** Get schema by global ID. */
    suspend fun getSchemaIdInfo(schemaId: Int): SchemaByIdResponse

    /** Register a new schema or new version of existing schema. */
    suspend fun registerSchema(subjectName: String, request: RegisterSchemaRequest): RegisterSchemaResponse

    /** Check if schema already exists. */
    suspend fun checkSchemaExists(subjectName: String, request: RegisterSchemaRequest): CheckSchemaExistsResponse

    /** Delete a subject (all versions). If permanent=true, hard delete; otherwise soft delete. */
    suspend fun deleteSubject(subjectName: String, permanent: Boolean = false): DeleteSubjectResponse

    /** Delete a specific schema version. If permanent=true, hard delete; otherwise soft delete. */
    suspend fun deleteSchemaVersion(subjectName: String, version: Long, permanent: Boolean = false): DeleteSchemaVersionResponse

    /** Get compatibility level for a specific subject. Returns null if using global default. */
    suspend fun getSubjectCompatibility(subjectName: String): CompatibilityResponse

    /** Get schema by GUID (used in V1 wire format with header-based schema references). */
    suspend fun getSchemaByGuid(guid: String): SchemaByIdResponse
}
