package io.confluent.intellijplugin.ccloud.model.response

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.confluent.intellijplugin.ccloud.model.CloudProvider
import io.confluent.intellijplugin.ccloud.model.CloudReference
import io.confluent.intellijplugin.ccloud.model.CloudSchemaRegistry

/**
 * Response DTO for Schema Registry list item.
 */
@JsonClass(generateAdapter = true)
data class SchemaRegistryResponse(
    @Json(name = "api_version") val apiVersion: String? = null,
    val kind: String? = null,
    val id: String,
    val metadata: Map<String, Any?>? = null,
    val spec: SchemaRegistrySpec? = null,
    val status: SchemaRegistryStatus? = null
) {
    fun toRepresentation(): CloudSchemaRegistry {
        if (spec == null) {
            return CloudSchemaRegistry(
                id = id,
                httpEndpoint = "",
                cloudProvider = CloudProvider.NONE,
                region = "",
                organization = null,
                environment = null
            )
        }
        val orgRef = spec.environment?.resourceName?.let { parseOrganization(it) }
        return CloudSchemaRegistry(
            id = id,
            httpEndpoint = spec.httpEndpoint ?: "",
            cloudProvider = CloudProvider.of(spec.cloud),
            region = spec.region ?: "",
            organization = orgRef,
            environment = spec.environment?.id?.let { CloudReference(it, null) }
        )
    }

    private fun parseOrganization(crnString: String): CloudReference? {
        val orgPattern = Regex("organization=([^/]+)")
        val match = orgPattern.find(crnString)
        return match?.groupValues?.get(1)?.let { orgId ->
            CloudReference(orgId, null)
        }
    }
}

@JsonClass(generateAdapter = true)
data class SchemaRegistrySpec(
    @Json(name = "display_name") val displayName: String? = null,
    val environment: EnvironmentReference? = null,
    @Json(name = "http_endpoint") val httpEndpoint: String? = null,
    val cloud: String? = null,
    val region: String? = null,
    @Json(name = "package") val governancePackage: String? = null
)

@JsonClass(generateAdapter = true)
data class SchemaRegistryStatus(
    val phase: String? = null
)


