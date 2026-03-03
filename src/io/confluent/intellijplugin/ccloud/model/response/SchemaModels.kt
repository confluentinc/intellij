package io.confluent.intellijplugin.ccloud.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias ListSubjectsResponse = List<String>

/** Schema info for UI (subject name + metadata). */
data class SchemaData(
    val name: String,
    val latestVersion: Int? = null,
    val schemaType: String? = null,
    val compatibility: String? = null
)

/** Schema metadata from enrichment. */
data class SchemaEnrichmentData(
    val latestVersion: Int? = null,
    val schemaType: String? = null,
    val compatibility: String? = null
)

typealias SchemaVersionsResponse = List<Long>

@Serializable
data class SchemaVersionResponse(
    @SerialName("subject") val subject: String,
    @SerialName("version") val version: Int,
    @SerialName("id") val id: Int,
    @SerialName("schema") val schema: String,
    @SerialName("schemaType") val schemaType: String? = null,
    @SerialName("references") val references: List<SchemaReferenceResponse>? = null
)

@Serializable
data class SchemaReferenceResponse(
    @SerialName("name") val name: String,
    @SerialName("subject") val subject: String,
    @SerialName("version") val version: Int
)

@Serializable
data class SchemaByIdResponse(
    @SerialName("schema") val schema: String,
    @SerialName("schemaType") val schemaType: String? = null,
    @SerialName("references") val references: List<SchemaReferenceResponse>? = null
)

/** Convert API response references to Schema Registry client library format. */
fun List<SchemaReferenceResponse>?.toSchemaReferences(): List<io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference> {
    return this?.map { ref ->
        io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference(
            ref.name,
            ref.subject,
            ref.version
        )
    } ?: emptyList()
}

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

// Write operation models

/** Request to register a new schema or new version of existing schema. */
@Serializable
data class RegisterSchemaRequest(
    @SerialName("schema") val schema: String,
    @SerialName("schemaType") val schemaType: String? = null,
    @SerialName("references") val references: List<SchemaReferenceResponse>? = null
)

/** Response from registering a schema. */
@Serializable
data class RegisterSchemaResponse(
    @SerialName("id") val id: Int
)

/** Response from checking if schema exists. */
@Serializable
data class CheckSchemaExistsResponse(
    @SerialName("subject") val subject: String,
    @SerialName("version") val version: Int,
    @SerialName("id") val id: Int,
    @SerialName("schema") val schema: String,
    @SerialName("schemaType") val schemaType: String? = null
)

/** Response from deleting schema or version (returns list of deleted version numbers). */
typealias DeleteSchemaResponse = List<Int>

// Compatibility operation models

/** Response from getting compatibility level. */
@Serializable
data class CompatibilityResponse(
    @SerialName("compatibilityLevel") val compatibilityLevel: String? = null
)

/** Request to update compatibility level. */
@Serializable
data class UpdateCompatibilityRequest(
    @SerialName("compatibility") val compatibility: String
)
