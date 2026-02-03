package io.confluent.intellijplugin.ccloud.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Represents a Confluent Cloud environment. */
@Serializable
data class Environment(
    val id: String,
    @SerialName("display_name") val displayName: String
)
