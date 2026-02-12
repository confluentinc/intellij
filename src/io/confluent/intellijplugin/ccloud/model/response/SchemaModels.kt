package io.confluent.intellijplugin.ccloud.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Response from GET /subjects - Returns list of schema names. */
typealias ListSchemasResponse = List<String>

/** Schema details for UI presentation. */
data class SchemaData(
    val name: String,
    val latestVersion: Int? = null,
    val schemaType: String? = null,
    val compatibility: String? = null
)

/** Enrichment data for schemas (requires additional API calls beyond basic schema list). */
data class SchemaEnrichmentData(
    val latestVersion: Int? = null,
    val schemaType: String? = null,
    val compatibility: String? = null
)

/** Response from GET /subjects/{subject}/versions - Returns version numbers. */
typealias SchemaVersionsResponse = List<Int>

/** Response from GET /subjects/{subject}/versions/{version} */
@Serializable
data class SchemaVersionResponse(
    @SerialName("subject") val subject: String,
    @SerialName("version") val version: Int,
    @SerialName("id") val id: Int,
    @SerialName("schema") val schema: String,
    @SerialName("schemaType") val schemaType: String? = null
)

/** Response from GET /schemas/ids/{id} */
@Serializable
data class SchemaByIdResponse(
    @SerialName("schema") val schema: String,
    @SerialName("schemaType") val schemaType: String? = null
)

/** Result of enriching a single schema with additional data. */
sealed class SchemaEnrichmentResult {
    abstract val schemaName: String
    abstract val progress: Pair<Int, Int>

    data class Success(
        override val schemaName: String,
        val data: SchemaEnrichmentData,
        override val progress: Pair<Int, Int>
    ) : SchemaEnrichmentResult()

    data class Failure(
        override val schemaName: String,
        override val progress: Pair<Int, Int>,
        val error: Exception
    ) : SchemaEnrichmentResult()
}
