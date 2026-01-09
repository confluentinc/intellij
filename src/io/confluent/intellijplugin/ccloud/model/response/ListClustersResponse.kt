package io.confluent.intellijplugin.ccloud.model.response

import io.confluent.intellijplugin.ccloud.client.ControlPlaneRestClient.ListMetadata
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Response from GET /cmk/v2/clusters?environment={envId} */
@Serializable
data class ListClustersResponse(
    val data: List<ClusterData> = emptyList(),
    val metadata: ListMetadata? = null
)

@Serializable
data class ClusterData(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    val spec: ClusterSpec? = null
)

@Serializable
data class ClusterSpec(
    val cloud: String? = null,
    val region: String? = null,
    val environment: ClusterEnvironment? = null
)

@Serializable
data class ClusterEnvironment(
    val id: String
)
