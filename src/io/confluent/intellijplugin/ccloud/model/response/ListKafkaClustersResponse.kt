package io.confluent.intellijplugin.ccloud.model.response

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Response DTO for Kafka clusters list.
 */
@JsonClass(generateAdapter = true)
data class ListKafkaClustersResponse(
    @Json(name = "api_version") val apiVersion: String? = null,
    val kind: String? = null,
    val metadata: ListMetadata? = null,
    val data: List<KafkaClusterResponse>
)


