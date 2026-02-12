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
 * Schema details for UI presentation.
 * Combines schema name with additional metadata.
 */
data class SchemaData(
    val name: String,
    val latestVersion: Int? = null,
    val schemaType: String? = null,
    val compatibility: String? = null
)
