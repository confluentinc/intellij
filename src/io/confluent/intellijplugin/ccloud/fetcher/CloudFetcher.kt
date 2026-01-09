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
     * Get environments.
     */
    suspend fun getEnvironments(): List<Environment>

    /**
     * Get Kafka clusters for an environment.
     */
    suspend fun getKafkaClusters(envId: String): List<Cluster>

    /**
     * Get Schema Registry cluster for an environment.
     */
    suspend fun getSchemaRegistry(envId: String): List<SchemaRegistry>
}

