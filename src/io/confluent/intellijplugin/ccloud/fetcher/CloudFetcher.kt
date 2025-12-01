package io.confluent.intellijplugin.ccloud.fetcher

import io.confluent.intellijplugin.ccloud.model.*

/**
 * Interface for fetching Confluent Cloud control plane resources.
 * 
 * This interface provides a clean abstraction for fetching resources from
 * Confluent Cloud control plane API. Implementations handle the actual
 * HTTP communication and response parsing.
 */
interface CloudFetcher {
    /**
     * Get Kafka clusters for a connection and environment.
     */
    suspend fun getKafkaClusters(
        connectionId: String,
        envId: String
    ): List<KafkaCluster>

    /**
     * Get Schema Registry for a connection and environment.
     */
    suspend fun getSchemaRegistry(connectionId: String, envId: String): SchemaRegistry?
}

