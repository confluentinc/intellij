package io.confluent.intellijplugin.ccloud.model.response

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Response DTO for Kafka clusters list API.
 * Based on Confluent Cloud API: GET /cmk/v2/clusters?environment={envId}
 */
@JsonClass(generateAdapter = true)
data class ListKafkaClustersResponse(
    @Json(name = "data")
    val data: List<KafkaClusterData>?
)

@JsonClass(generateAdapter = true)
data class KafkaClusterData(
    @Json(name = "id")
    val id: String,
    
    @Json(name = "display_name")
    val displayName: String?,
    
    @Json(name = "spec")
    val spec: KafkaClusterSpec?
)

@JsonClass(generateAdapter = true)
data class KafkaClusterSpec(
    @Json(name = "cloud")
    val cloud: String?,
    
    @Json(name = "region")
    val region: String?,
    
    @Json(name = "environment")
    val environment: KafkaClusterEnvironment?
)

@JsonClass(generateAdapter = true)
data class KafkaClusterEnvironment(
    @Json(name = "id")
    val id: String
)
