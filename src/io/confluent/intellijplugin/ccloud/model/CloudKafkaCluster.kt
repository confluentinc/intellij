package io.confluent.intellijplugin.ccloud.model

/**
 * Represents a Confluent Cloud Kafka cluster
 */
data class KafkaCluster(
    val id: String,
    val displayName: String,
    val cloudProvider: String,
    val region: String
)

