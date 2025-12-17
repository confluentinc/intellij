package io.confluent.intellijplugin.ccloud.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Response from GET /srcm/v3/clusters?environment={envId} */
@Serializable
data class ListSchemaRegistriesResponse(
    val data: List<SchemaRegistryData> = emptyList()
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
