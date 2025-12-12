package io.confluent.intellijplugin.ccloud.model

/**
 * Represents a Confluent Cloud Schema Registry cluster.
 */
data class SchemaRegistry(
    val id: String,
    val displayName: String,
    val cloudProvider: String = "Unknown",
    val region: String = "Unknown",
    val httpEndpoint: String = ""
)

