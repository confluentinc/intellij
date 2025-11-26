package io.confluent.intellijplugin.ccloud.fetcher

import io.confluent.intellijplugin.ccloud.client.CloudRestClient
import io.confluent.intellijplugin.ccloud.config.CloudConfig
import io.confluent.intellijplugin.ccloud.model.*

/**
 * Implementation of CloudFetcher that makes REST API calls to Confluent Cloud control plane.
 */
class CloudFetcherImpl(
    getHeaders: (String) -> Map<String, String>,
    baseUrl: String = CloudConfig.CONTROL_PLANE_BASE_URL
) : CloudRestClient(getHeaders, baseUrl), CloudFetcher {

    override suspend fun getConnections(): List<CloudConnection> {
        TODO("Not yet implemented")
    }

    override suspend fun getConnection(connectionId: String): CloudConnection? {
        TODO("Not yet implemented")
    }

    override suspend fun getOrganizations(connectionId: String): List<CloudOrganization> {
        TODO("Not yet implemented")
    }

    override suspend fun getEnvironments(connectionId: String, limits: PageLimits?): List<CloudEnvironment> {
        TODO("Not yet implemented")
    }

    override suspend fun getKafkaClusters(
        connectionId: String,
        envId: String,
        limits: PageLimits?
    ): List<CloudKafkaCluster> {
        TODO("Not yet implemented")
    }

    override suspend fun getSchemaRegistry(connectionId: String, envId: String): CloudSchemaRegistry? {
        TODO("Not yet implemented")
    }
}

