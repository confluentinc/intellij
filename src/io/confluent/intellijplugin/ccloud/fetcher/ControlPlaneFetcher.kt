package io.confluent.intellijplugin.ccloud.fetcher

import io.confluent.intellijplugin.ccloud.model.Cluster
import io.confluent.intellijplugin.ccloud.model.Environment
import io.confluent.intellijplugin.ccloud.model.SchemaRegistry

/**
 * Control plane operations for Confluent Cloud (environments, clusters, schema registries).
 */
interface ControlPlaneFetcher {
    /** Get all environments. */
    suspend fun getEnvironments(): List<Environment>

    /** Get Kafka clusters for an environment. */
    suspend fun getKafkaClusters(envId: String): List<Cluster>

    /** Get Schema Registry for an environment (0 or 1 per environment). */
    suspend fun getSchemaRegistry(envId: String): SchemaRegistry?
}
