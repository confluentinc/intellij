package io.confluent.intellijplugin.ccloud.model.response

import io.confluent.intellijplugin.ccloud.client.CloudRestClient.ListMetadata
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Response from GET /cmk/v2/clusters?environment={envId} */
@Serializable
data class ListKafkaClustersResponse(
    val data: List<KafkaClusterData> = emptyList(),
    val metadata: ListMetadata? = null
)

@Serializable
data class KafkaClusterData(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    val spec: KafkaClusterSpec? = null
)

@Serializable
data class KafkaClusterSpec(
    val cloud: String? = null,
    val region: String? = null,
    val environment: KafkaClusterEnvironment? = null
)

@Serializable
data class KafkaClusterEnvironment(
    val id: String
)
