package io.confluent.intellijplugin.ccloud.model.response

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.confluent.intellijplugin.ccloud.model.CloudOrganization

/**
 * Response DTO for organization list item.
 */
@JsonClass(generateAdapter = true)
data class OrganizationResponse(
    @Json(name = "api_version") val apiVersion: String? = null,
    val kind: String? = null,
    val id: String,
    val metadata: Map<String, Any?>? = null,
    @Json(name = "display_name") val displayName: String? = null,
    @Json(name = "jit_enabled") val jitEnabled: Boolean? = null
) {
    fun toRepresentation(): CloudOrganization {
        return CloudOrganization(
            id = id,
            displayName = displayName ?: "",
            isCurrent = false
        )
    }
}

