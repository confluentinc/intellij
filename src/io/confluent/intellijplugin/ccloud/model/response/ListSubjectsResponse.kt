package io.confluent.intellijplugin.ccloud.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response from GET /subjects
 * Returns a simple array of subject names.
 *
 * Example: ["topic1-key", "topic1-value", "topic2-value"]
 *
 * @see <a href="https://docs.confluent.io/cloud/current/api.html#tag/Schemas-(v1)/operation/getSubjects">Get Subjects API</a>
 */
typealias ListSubjectsResponse = List<String>

/**
 * Response from GET /subjects/{subject}/versions
 * Returns array of version numbers.
 *
 * Example: [1, 2, 3]
 */
typealias SubjectVersionsResponse = List<Int>

/**
 * Response from GET /subjects/{subject}/versions/{version}
 *
 * @see <a href="https://docs.confluent.io/cloud/current/api.html#tag/Schemas-(v1)/operation/getSchemaByVersion">Get Schema by Version API</a>
 */
@Serializable
data class SchemaVersionResponse(
    @SerialName("subject") val subject: String,
    @SerialName("version") val version: Int,
    @SerialName("id") val id: Int,
    @SerialName("schema") val schema: String,
    @SerialName("schemaType") val schemaType: String? = null
)

/**
 * Response from GET /schemas/ids/{id}
 */
@Serializable
data class SchemaByIdResponse(
    @SerialName("schema") val schema: String,
    @SerialName("schemaType") val schemaType: String? = null
)

/**
 * Subject details for UI presentation.
 * Combines subject name with additional metadata.
 */
data class SubjectData(
    val name: String,
    val latestVersion: Int? = null,
    val schemaType: String? = null,
    val compatibility: String? = null
)

/**
 * Request for POST /subjects/{subject}/versions (register schema)
 * and POST /subjects/{subject} (check if schema exists).
 */
@Serializable
data class RegisterSchemaRequest(
    @SerialName("schema") val schema: String,
    @SerialName("schemaType") val schemaType: String = "AVRO",
    @SerialName("references") val references: List<SchemaReference> = emptyList()
)

/**
 * Schema reference for nested/imported schemas.
 */
@Serializable
data class SchemaReference(
    @SerialName("name") val name: String,
    @SerialName("subject") val subject: String,
    @SerialName("version") val version: Int
)

/**
 * Response from POST /subjects/{subject}/versions
 * Returns the ID of the registered schema.
 */
@Serializable
data class RegisterSchemaResponse(
    @SerialName("id") val id: Int
)

/**
 * Response from DELETE /subjects/{subject}
 * Returns array of deleted version numbers.
 */
typealias DeleteSubjectResponse = List<Int>

/**
 * Response from DELETE /subjects/{subject}/versions/{version}
 * Returns the deleted version number.
 */
typealias DeleteVersionResponse = Int

/**
 * Response from GET /config or GET /config/{subject}
 */
@Serializable
data class CompatibilityConfigResponse(
    @SerialName("compatibilityLevel") val compatibilityLevel: String? = null,
    @SerialName("compatibility") val compatibility: String? = null
) {
    /** Get compatibility value from either field (API uses both names). */
    val level: String?
        get() = compatibilityLevel ?: compatibility
}

/**
 * Request for PUT /config/{subject}
 */
@Serializable
data class UpdateCompatibilityRequest(
    @SerialName("compatibility") val compatibility: String
)
