package io.confluent.intellijplugin.ccloud.model.response

import io.confluent.intellijplugin.ccloud.client.CCloudRestClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Generic wrapper for paginated Confluent Cloud Data Plane API responses.
 * Made generic to avoid duplicating the same `kind`/`metadata`/`data` structure for each resource type
 * (topics, partitions, configs all share this pagination format).
 */
@Serializable
data class DataPlaneListResponse<T>(
    @SerialName("kind") val kind: String? = null,
    @SerialName("metadata") val metadata: CCloudRestClient.ListMetadata,
    @SerialName("data") val data: List<T>
)

interface DataPlaneResource {
    val kind: String
}
