package io.confluent.intellijplugin.ccloud.fetcher

import io.confluent.intellijplugin.ccloud.client.CloudRestClient
import io.confluent.intellijplugin.ccloud.config.CloudConfig
import io.confluent.intellijplugin.ccloud.model.*
import io.confluent.intellijplugin.ccloud.model.response.ListEnvironmentsResponse
import io.confluent.intellijplugin.ccloud.model.response.ListKafkaClustersResponse
import io.confluent.intellijplugin.ccloud.model.response.ListSchemaRegistriesResponse
import io.confluent.intellijplugin.core.serializer.BdtJson

/**
 * Implementation of CloudFetcher that makes REST API calls to Confluent Cloud control plane.
 * Uses OAuth authentication via CCloudAuthService.
 */
class CloudFetcherImpl(
    baseUrl: String = CloudConfig.CONTROL_PLANE_BASE_URL
) : CloudRestClient(baseUrl), CloudFetcher {

    override suspend fun getEnvironments(connectionId: String): List<CCloudEnvironment> {
        val headers = headersFor()
        return listItems(headers, CloudConfig.ControlPlane.ENV_LIST_URI) { jsonBody ->
            val response = BdtJson.fromJsonToClass(jsonBody, ListEnvironmentsResponse::class.java)
            response.data?.map { envData ->
                CCloudEnvironment(
                    id = envData.id,
                    displayName = envData.displayName ?: envData.id
                )
            } ?: emptyList()
        }
    }

    override suspend fun getKafkaClusters(
        connectionId: String,
        envId: String
    ): List<KafkaCluster> {
        val headers = headersFor()
        val uri = String.format(CloudConfig.ControlPlane.LKC_LIST_URI, envId)
        return listItems(headers, uri) { jsonBody ->
            val response = BdtJson.fromJsonToClass(jsonBody, ListKafkaClustersResponse::class.java)
            response.data?.map { clusterData ->
                KafkaCluster(
                    id = clusterData.id,
                    displayName = clusterData.displayName ?: clusterData.id,
                    cloudProvider = clusterData.spec?.cloud ?: "Unknown",
                    region = clusterData.spec?.region ?: "Unknown"
                )
            } ?: emptyList()
        }
    }

    override suspend fun getSchemaRegistries(connectionId: String, envId: String): List<SchemaRegistry> {
        val headers = headersFor()
        val uri = String.format(CloudConfig.ControlPlane.SR_LIST_URI, envId)
        return listItems(headers, uri) { jsonBody ->
            val response = BdtJson.fromJsonToClass(jsonBody, ListSchemaRegistriesResponse::class.java)
            response.data?.map { srData ->
                SchemaRegistry(
                    id = srData.id,
                    displayName = srData.spec?.displayName ?: srData.id,
                    cloudProvider = srData.spec?.cloud ?: "Unknown",
                    region = srData.spec?.region ?: "Unknown",
                    httpEndpoint = srData.spec?.httpEndpoint ?: ""
                )
            } ?: emptyList()
        }
    }
}

