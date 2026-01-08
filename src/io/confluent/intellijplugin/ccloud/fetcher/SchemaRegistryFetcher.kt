package io.confluent.intellijplugin.ccloud.fetcher

import io.confluent.intellijplugin.ccloud.model.response.SchemaByIdResponse
import io.confluent.intellijplugin.ccloud.model.response.SchemaVersionResponse
import io.confluent.intellijplugin.ccloud.model.response.SubjectData

/**
 * Interface for Schema Registry data plane operations via REST API v1.
 *
 * Requires "target-sr-cluster" header for all requests.
 *
 * @see <a href="https://docs.confluent.io/cloud/current/api.html#tag/Schemas-(v1)">Schema Registry API v1</a>
 */
interface SchemaRegistryFetcher {
    // Subjects

    /**
     * List all subjects (schema subjects) in the Schema Registry.
     * A subject typically corresponds to a topic-key or topic-value.
     *
     * @return List of subject names (e.g., ["topic1-key", "topic1-value"])
     * @see <a href="https://docs.confluent.io/cloud/current/api.html#tag/Schemas-(v1)/operation/getSubjects">List Subjects</a>
     */
    suspend fun listSubjects(): List<String>

    /**
     * Get detailed information about subjects for UI presentation.
     * Enriches basic subject list with version and type information.
     *
     * @return List of SubjectData with metadata
     */
    suspend fun listSubjectsWithDetails(): List<SubjectData>

    // TODO: Future implementations
    // suspend fun deleteSubject(subject: String, permanent: Boolean = false)

    // Schema Versions

    /**
     * List all version numbers for a subject.
     *
     * @param subject The subject name (e.g., "my-topic-value")
     * @return List of version numbers (e.g., [1, 2, 3])
     * @see <a href="https://docs.confluent.io/cloud/current/api.html#tag/Schemas-(v1)/operation/getVersions">List Versions</a>
     */
    suspend fun listSubjectVersions(subject: String): List<Int>

    /**
     * Get a specific version of a schema for a subject.
     *
     * @param subject The subject name
     * @param version The version number (or "latest")
     * @return Schema version details including schema content
     * @see <a href="https://docs.confluent.io/cloud/current/api.html#tag/Schemas-(v1)/operation/getSchemaByVersion">Get Schema by Version</a>
     */
    suspend fun getSchemaByVersion(subject: String, version: String): SchemaVersionResponse

    // Schemas by ID

    /**
     * Get a schema by its global ID.
     *
     * @param schemaId The global schema ID
     * @return Schema content
     * @see <a href="https://docs.confluent.io/cloud/current/api.html#tag/Schemas-(v1)/operation/getSchema">Get Schema by ID</a>
     */
    suspend fun getSchemaById(schemaId: Int): SchemaByIdResponse

    // Configuration

    // TODO: Future implementations
    // suspend fun getGlobalConfig(): Map<String, String>
    // suspend fun getSubjectConfig(subject: String): Map<String, String>
    // suspend fun updateSubjectConfig(subject: String, config: Map<String, String>)
}
