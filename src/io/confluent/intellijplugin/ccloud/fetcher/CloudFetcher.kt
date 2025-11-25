package io.confluent.intellijplugin.ccloud.fetcher

import io.confluent.intellijplugin.ccloud.model.*

/**
 * Interface for fetching Confluent Cloud control plane resources.
 */
interface CloudFetcher {
    /**
     * Get all Confluent Cloud connections.
     */
    suspend fun getConnections(): List<CloudConnection>

    /**
     * Get a specific connection by ID.
     */
    suspend fun getConnection(connectionId: String): CloudConnection?

    /**
     * Get organizations for a connection.
     */
    suspend fun getOrganizations(connectionId: String): List<CloudOrganization>

    /**
     * Get environments for a connection.
     */
    suspend fun getEnvironments(connectionId: String, limits: PageLimits? = null): List<CloudEnvironment>

    /**
     * Get Kafka clusters for a connection and environment.
     */
    suspend fun getKafkaClusters(
        connectionId: String,
        envId: String,
        limits: PageLimits? = null
    ): List<CloudKafkaCluster>

    /**
     * Get Kafka clusters matching search criteria.
     */
    suspend fun getKafkaClusters(
        connectionId: String,
        envId: String,
        criteria: CloudSearchCriteria,
        limits: PageLimits? = null
    ): List<CloudKafkaCluster> {
        return getKafkaClusters(connectionId, envId, limits)
            .filter { it.matches(criteria) }
    }

    /**
     * Find Kafka clusters across all environments matching criteria.
     */
    suspend fun findKafkaClusters(
        connectionId: String,
        criteria: CloudSearchCriteria,
        envLimits: PageLimits? = null,
        clusterLimits: PageLimits? = null
    ): List<CloudKafkaCluster> {
        val environments = getEnvironments(connectionId, envLimits)
        return environments.flatMap { env ->
            getKafkaClusters(connectionId, env.id, criteria, clusterLimits)
        }
    }

    /**
     * Find a specific Kafka cluster by ID.
     */
    suspend fun findKafkaCluster(connectionId: String, lkcId: String): CloudKafkaCluster? {
        return findKafkaClusters(
            connectionId,
            CloudSearchCriteria.create().withResourceId(lkcId).build(),
            PageLimits.DEFAULT,
            PageLimits.DEFAULT
        ).firstOrNull()
    }

    /**
     * Get Schema Registry for a connection and environment.
     */
    suspend fun getSchemaRegistry(connectionId: String, envId: String): CloudSchemaRegistry?

    /**
     * Find Schema Registry by ID.
     */
    suspend fun findSchemaRegistry(connectionId: String, lsrcId: String): CloudSchemaRegistry? {
        val environments = getEnvironments(connectionId, PageLimits.DEFAULT)
        return environments.mapNotNull { env ->
            getSchemaRegistry(connectionId, env.id)
        }.firstOrNull { it.id.equals(lsrcId, ignoreCase = true) }
    }
}

