package io.confluent.intellijplugin.ccloud.model.response

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.confluent.intellijplugin.ccloud.model.CCloudEnvironment

/**
 * Response DTO for environments list API.
 */
@JsonClass(generateAdapter = true)
data class ListEnvironmentsResponse(
    @Json(name = "data")
    val data: List<EnvironmentData>?
)

@JsonClass(generateAdapter = true)
data class EnvironmentData(
    @Json(name = "id")
    val id: String,
    @Json(name = "display_name")
    val displayName: String?
)

