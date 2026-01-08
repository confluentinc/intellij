package io.confluent.intellijplugin.ccloud.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Represents a Confluent Cloud Kafka cluster. */
@Serializable
data class KafkaCluster(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("cloud") val cloudProvider: String,
    val region: String,
    @SerialName("http_endpoint") val httpEndpoint: String
)

/**
 * Constructs the REST endpoint URL for this cluster's data plane API.
 * Uses the http_endpoint from the cluster response.
 * Example: https://pkc-12345.us-west-2.aws.confluent.cloud:443
 */
val KafkaCluster.restEndpoint: String
    get() = httpEndpoint.removeSuffix(":443")
