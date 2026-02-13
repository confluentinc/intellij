package io.confluent.intellijplugin.ccloud.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias ListSubjectsResponse = List<String>

/** Schema info for UI (subject name + metadata). */
data class SchemaData(
    val name: String,
    val latestVersion: Int? = null,
    val schemaType: String? = null
)

/** Schema metadata from enrichment. */
data class SchemaEnrichmentData(
    val latestVersion: Int? = null,
    val schemaType: String? = null
)

typealias SchemaVersionsResponse = List<Long>

@Serializable
data class SchemaVersionResponse(
    @SerialName("subject") val subject: String,
    @SerialName("version") val version: Int,
    @SerialName("id") val id: Int,
    @SerialName("schema") val schema: String,
    @SerialName("schemaType") val schemaType: String? = null
)

@Serializable
data class SchemaByIdResponse(
    @SerialName("schema") val schema: String,
    @SerialName("schemaType") val schemaType: String? = null
)

/** Result of schema enrichment. */
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
