package io.confluent.intellijplugin.ccloud.model.response

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.confluent.intellijplugin.ccloud.model.CloudEnvironment
import io.confluent.intellijplugin.ccloud.model.CloudGovernancePackage
import io.confluent.intellijplugin.ccloud.model.CloudReference

/**
 * Response DTO for environment list item.
 */
@JsonClass(generateAdapter = true)
data class EnvironmentResponse(
    @Json(name = "api_version") val apiVersion: String? = null,
    val kind: String? = null,
    val id: String,
    val metadata: EnvironmentMetadata? = null,
    @Json(name = "display_name") val displayName: String? = null,
    @Json(name = "jit_enabled") val jitEnabled: Boolean? = null,
    @Json(name = "stream_governance_config") val governance: GovernanceConfig? = null
) {
    fun toRepresentation(): CloudEnvironment {
        val orgRef = metadata?.resourceName?.let { parseOrganization(it) }
        val governancePackage = parsePackage(governance)
        return CloudEnvironment(
            id = id,
            displayName = displayName ?: "",
            organization = orgRef,
            governancePackage = governancePackage
        )
    }

    private fun parsePackage(governance: GovernanceConfig?): CloudGovernancePackage {
        if (governance == null) return CloudGovernancePackage.NONE
        return try {
            CloudGovernancePackage.valueOf(governance.packageName.uppercase())
        } catch (e: IllegalArgumentException) {
            CloudGovernancePackage.NONE
        }
    }

    private fun parseOrganization(crnString: String): CloudReference? {
        // Parse CRN: crn://confluent.cloud/organization=23b1185e-d874-4f61-81d6-c9c61aa8969c/environment=env-123
        val orgPattern = Regex("organization=([^/]+)")
        val match = orgPattern.find(crnString)
        return match?.groupValues?.get(1)?.let { orgId ->
            CloudReference(orgId, null)
        }
    }
}

@JsonClass(generateAdapter = true)
data class EnvironmentMetadata(
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "resource_name") val resourceName: String? = null,
    val self: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class GovernanceConfig(
    @Json(name = "package") val packageName: String? = null
)

