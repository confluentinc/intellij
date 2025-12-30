package io.confluent.intellijplugin.ccloud.model.response

import io.confluent.intellijplugin.ccloud.client.CloudRestClient.ListMetadata
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Response from GET /org/v2/environments */
@Serializable
data class ListEnvironmentsResponse(
    val data: List<EnvironmentData> = emptyList(),
    val metadata: ListMetadata? = null
)

@Serializable
data class EnvironmentData(
    val id: String,
    @SerialName("display_name") val displayName: String? = null
)
