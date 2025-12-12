package io.confluent.intellijplugin.ccloud.model.response

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Response DTO for Schema Registry clusters list API.
 * Based on Confluent Cloud API: GET /srcm/v3/clusters?environment={envId}
 */
@JsonClass(generateAdapter = true)
data class ListSchemaRegistriesResponse(
    @Json(name = "data")
    val data: List<SchemaRegistryData>?
)

@JsonClass(generateAdapter = true)
data class SchemaRegistryData(
    @Json(name = "id")
    val id: String,

    @Json(name = "spec")
    val spec: SchemaRegistrySpec?
)

@JsonClass(generateAdapter = true)
data class SchemaRegistrySpec(
    @Json(name = "display_name")
    val displayName: String?,

    @Json(name = "cloud")
    val cloud: String?,

    @Json(name = "region")
    val region: String?,

    @Json(name = "http_endpoint")
    val httpEndpoint: String?
)
