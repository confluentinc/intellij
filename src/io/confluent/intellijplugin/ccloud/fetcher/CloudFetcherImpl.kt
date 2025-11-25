package io.confluent.intellijplugin.ccloud.fetcher

import com.squareup.moshi.JsonClass
import io.confluent.intellijplugin.ccloud.auth.CloudOAuthProvider
import io.confluent.intellijplugin.ccloud.client.CloudRestClient
import io.confluent.intellijplugin.ccloud.exception.CloudResourceFetchingException
import io.confluent.intellijplugin.ccloud.config.CloudConfig
import io.confluent.intellijplugin.ccloud.model.*
import io.confluent.intellijplugin.ccloud.model.response.*

/**
 * Implementation of CloudFetcher that makes REST API calls to Confluent Cloud control plane.
 */
class CloudFetcherImpl(
    oauthProvider: CloudOAuthProvider,
    baseUrl: String = CloudConfig.CONTROL_PLANE_BASE_URL
) : CloudRestClient(oauthProvider, baseUrl), CloudFetcher {

    override suspend fun getConnections(): List<CloudConnection> {
        // TODO: Implement via connection manager
        return emptyList()
    }

    override suspend fun getConnection(connectionId: String): CloudConnection? {
        // TODO: Implement via connection manager
        return null
    }

    override suspend fun getOrganizations(connectionId: String): List<CloudOrganization> {
        val headers = headersFor(connectionId)
        val url = "$baseUrl${CloudConfig.ControlPlane.ORG_LIST_URI}"
        return listItems(headers, url, null, ::parseOrganizationsList)
            .map { it.withConnectionId(connectionId) }
    }

    override suspend fun getEnvironments(connectionId: String, limits: PageLimits?): List<CloudEnvironment> {
        val headers = headersFor(connectionId)
        val url = "$baseUrl${CloudConfig.ControlPlane.ENV_LIST_URI}"
        return listItems(headers, url, limits, ::parseEnvironmentsList)
            .map { it.withConnectionId(connectionId) }
    }

    override suspend fun getKafkaClusters(
        connectionId: String,
        envId: String,
        limits: PageLimits?
    ): List<CloudKafkaCluster> {
        val headers = headersFor(connectionId)
        val url = "$baseUrl${String.format(CloudConfig.ControlPlane.LKC_LIST_URI, envId)}"
        return listItems(headers, url, limits, ::parseKafkaClustersList)
            .map { it.withConnectionId(connectionId) }
    }

    override suspend fun getSchemaRegistry(connectionId: String, envId: String): CloudSchemaRegistry? {
        val headers = headersFor(connectionId)
        val url = "$baseUrl${String.format(CloudConfig.ControlPlane.SR_LIST_URI, envId)}"
        return try {
            val response = getItem(headers, url) { json ->
                parseRawItem(url, json, SchemaRegistryListResponse::class.java)
            }
            
            response.data.firstOrNull()?.toRepresentation()?.let { registry ->
                registry.withConnectionId(connectionId)
            }
        } catch (e: CloudResourceFetchingException) {
            // Schema Registry may not exist for all environments
            null
        }
    }

    private fun parseOrganizationsList(
        json: String,
        @Suppress("UNUSED_PARAMETER") _state: PaginationState
    ): PageOfResults<CloudOrganization> {
        val adapter = moshi.adapter(ListOrganizationsResponse::class.java)
        val response = adapter.fromJson(json)
            ?: throw CloudResourceFetchingException("Failed to parse organizations response: $json")
        val items = response.data.map { it.toRepresentation() }
        return PageOfResults(items, response.metadata)
    }

    private fun parseEnvironmentsList(
        json: String,
        @Suppress("UNUSED_PARAMETER") _state: PaginationState
    ): PageOfResults<CloudEnvironment> {
        val adapter = moshi.adapter(ListEnvironmentsResponse::class.java)
        val response = adapter.fromJson(json)
            ?: throw CloudResourceFetchingException("Failed to parse environments response: $json")
        val items = response.data.map { it.toRepresentation() }
        return PageOfResults(items, response.metadata)
    }

    private fun parseKafkaClustersList(
        json: String,
        @Suppress("UNUSED_PARAMETER") _state: PaginationState
    ): PageOfResults<CloudKafkaCluster> {
        val adapter = moshi.adapter(ListKafkaClustersResponse::class.java)
        val response = adapter.fromJson(json)
            ?: throw CloudResourceFetchingException("Failed to parse Kafka clusters response: $json")
        val items = response.data.map { it.toRepresentation() }
        return PageOfResults(items, response.metadata)
    }
}

