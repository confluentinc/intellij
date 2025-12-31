package io.confluent.intellijplugin.ccloud.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Represents a Confluent Cloud Kafka cluster. */
@Serializable
data class KafkaCluster(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("cloud") val cloudProvider: String,
    val region: String
)
