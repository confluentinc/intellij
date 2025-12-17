package io.confluent.intellijplugin.ccloud.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Represents a Confluent Cloud Schema Registry cluster. */
@Serializable
data class SchemaRegistry(
    val id: String,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("cloud") val cloudProvider: String = "",
    val region: String = "",
    @SerialName("http_endpoint") val httpEndpoint: String = ""
)
