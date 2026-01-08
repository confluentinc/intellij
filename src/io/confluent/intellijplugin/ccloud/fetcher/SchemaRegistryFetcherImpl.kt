package io.confluent.intellijplugin.ccloud.fetcher

import io.confluent.intellijplugin.ccloud.client.DataPlaneRestClient
import io.confluent.intellijplugin.ccloud.config.CloudConfig
import io.confluent.intellijplugin.ccloud.model.response.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Schema Registry data plane operations via REST API v1.
 *
 * Requires "target-sr-cluster" header (set in DataPlaneRestClient).
 *
 * @param client Data plane REST client with SR-specific headers
 * @param schemaRegistryClusterId Schema Registry cluster ID (lsrc-xxxxx)
 * @see <a href="https://docs.confluent.io/cloud/current/api.html#tag/Schemas-(v1)">Schema Registry API v1</a>
 */
class SchemaRegistryFetcherImpl(
    private val client: DataPlaneRestClient,
    private val schemaRegistryClusterId: String
) : SchemaRegistryFetcher {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * List all subjects in the Schema Registry.
     * Returns a simple list of subject names.
     *
     * @return List of subject names
     */
    override suspend fun listSubjects(): List<String> {
        val path = CloudConfig.DataPlane.SchemaRegistry.SUBJECTS_URI

        return client.fetch(path) { body ->
            // Response is a simple JSON array: ["subject1", "subject2"]
            json.decodeFromString(ListSerializer(String.serializer()), body)
        }
    }

    /**
     * List subjects with detailed information.
     * This method enriches the basic subject list by fetching the latest version
     * for each subject to get schema type and other metadata.
     *
     * @return List of SubjectData with metadata
     */
    override suspend fun listSubjectsWithDetails(): List<SubjectData> {
        val subjects = listSubjects()

        // TODO: In the future, we can parallelize these calls for better performance
        return subjects.map { subject ->
            try {
                // Get latest version to extract metadata
                val latestSchema = getSchemaByVersion(subject, "latest")
                SubjectData(
                    name = subject,
                    latestVersion = latestSchema.version,
                    schemaType = latestSchema.schemaType,
                    compatibility = null // Would require additional API call to /config/{subject}
                )
            } catch (e: Exception) {
                // If we can't get details, return basic info
                SubjectData(name = subject)
            }
        }
    }

    /**
     * List all version numbers for a subject.
     *
     * @param subject The subject name
     * @return List of version numbers
     */
    override suspend fun listSubjectVersions(subject: String): List<Int> {
        val path = String.format(CloudConfig.DataPlane.SchemaRegistry.SUBJECT_VERSIONS_URI, subject)

        return client.fetch(path) { body ->
            // Response is a JSON array of integers: [1, 2, 3]
            json.decodeFromString(ListSerializer(Int.serializer()), body)
        }
    }

    /**
     * Get a specific version of a schema.
     *
     * @param subject The subject name
     * @param version The version number or "latest"
     * @return Schema version response
     */
    override suspend fun getSchemaByVersion(subject: String, version: String): SchemaVersionResponse {
        val path = String.format(CloudConfig.DataPlane.SchemaRegistry.SUBJECT_VERSION_URI, subject, version)

        return client.fetch(path) { body ->
            json.decodeFromString<SchemaVersionResponse>(body)
        }
    }

    /**
     * Get a schema by its global ID.
     *
     * @param schemaId The global schema ID
     * @return Schema content
     */
    override suspend fun getSchemaById(schemaId: Int): SchemaByIdResponse {
        val path = String.format(CloudConfig.DataPlane.SchemaRegistry.SCHEMA_BY_ID_URI, schemaId)

        return client.fetch(path) { body ->
            json.decodeFromString<SchemaByIdResponse>(body)
        }
    }
}
