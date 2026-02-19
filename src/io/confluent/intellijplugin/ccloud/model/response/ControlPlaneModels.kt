package io.confluent.intellijplugin.ccloud.model.response

import io.confluent.intellijplugin.ccloud.client.CCloudRestClient.ListMetadata
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request/response models for the Confluent Cloud Control Plane APIs.
 * Includes environments, Kafka clusters, and Schema Registry cluster.
 */


/** Response from GET /org/v2/environments */
@Serializable
data class ListEnvironmentsResponse(
    val data: List<EnvironmentData> = emptyList(),
    val metadata: ListMetadata? = null
)

@Serializable
data class EnvironmentData(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("stream_governance_config") val streamGovernanceConfig: StreamGovernanceConfig? = null
)

@Serializable
data class StreamGovernanceConfig(
    @SerialName("package") val packageName: String? = null
)

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
    @SerialName("display_name") val displayName: String? = null,
    val cloud: String? = null,
    val region: String? = null,
    @SerialName("http_endpoint") val httpEndpoint: String? = null,
    val environment: ClusterEnvironment? = null
)

@Serializable
data class ClusterEnvironment(
    val id: String
)

/** Response from GET /srcm/v3/clusters?environment={envId} (lists Schema Registry cluster). */
@Serializable
data class ListSchemaRegistryResponse(
    val data: List<SchemaRegistryData> = emptyList(),
    val metadata: ListMetadata? = null
)

@Serializable
data class SchemaRegistryData(
    val id: String,
    val spec: SchemaRegistrySpec? = null
)

@Serializable
data class SchemaRegistrySpec(
    @SerialName("display_name") val displayName: String? = null,
    val cloud: String? = null,
    val region: String? = null,
    @SerialName("http_endpoint") val httpEndpoint: String? = null
)
