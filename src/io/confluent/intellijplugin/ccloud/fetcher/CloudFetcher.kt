package io.confluent.intellijplugin.ccloud.fetcher

import io.confluent.intellijplugin.ccloud.model.*

/**
 * Control plane operations for Confluent Cloud (environments, clusters, schema registries).
 */
interface CloudFetcher {
    /** Get all environments. */
    suspend fun getEnvironments(): List<Environment>

    /** Get Kafka clusters for an environment. */
    suspend fun getKafkaClusters(envId: String): List<Cluster>

    /** Get Schema Registry for an environment. */
    suspend fun getSchemaRegistry(envId: String): List<SchemaRegistry>
}
