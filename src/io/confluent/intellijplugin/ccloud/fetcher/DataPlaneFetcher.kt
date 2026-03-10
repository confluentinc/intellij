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

    suspend fun consumeRecords(topicName: String, request: ConsumeRecordsRequest): ConsumeRecordsResponse

    /** Get all subject names from Schema Registry. */
    suspend fun getAllSubjects(): List<String>

    /** Load schema info (version, type) for a subject. */
    suspend fun loadSchemaInfo(schemaName: String): SchemaData

    /** List all versions for a subject. */
    suspend fun listSchemaVersions(schemaName: String): List<Long>

    /** Get schema content for a specific version. */
    suspend fun getSchemaVersionInfo(schemaName: String, version: Long): SchemaVersionResponse

    /** Get latest schema version. */
    suspend fun getLatestVersionInfo(schemaName: String): SchemaVersionResponse

    /** Get schema by global ID. */
    suspend fun getSchemaIdInfo(schemaId: Int): SchemaByIdResponse

    /** Register a new schema or new version of existing schema. */
    suspend fun createSchema(schemaName: String, request: RegisterSchemaRequest): RegisterSchemaResponse

    /** Check if schema already exists. */
    suspend fun checkSchemaExists(schemaName: String, request: RegisterSchemaRequest): CheckSchemaExistsResponse

    /** Delete a subject (all versions). If permanent=true, hard delete; otherwise soft delete. */
    suspend fun deleteSchema(schemaName: String, permanent: Boolean = false): DeleteSubjectResponse

    /** Delete a specific schema version. If permanent=true, hard delete; otherwise soft delete. */
    suspend fun deleteSchemaVersion(schemaName: String, version: Long, permanent: Boolean = false): DeleteSchemaVersionResponse

    /** Get compatibility level for a specific subject. Returns null if using global default. */
    suspend fun getSchemaCompatibility(schemaName: String): CompatibilityResponse

    /** Get schema by GUID (used in V1 wire format with header-based schema references). */
    suspend fun getSchemaByGuid(guid: String): SchemaByIdResponse
}
