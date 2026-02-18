package io.confluent.intellijplugin.ccloud.fetcher

import io.confluent.intellijplugin.ccloud.client.CCloudRestClient
import io.confluent.intellijplugin.ccloud.config.CloudConfig
import io.confluent.intellijplugin.ccloud.model.*
import io.confluent.intellijplugin.ccloud.model.response.ListEnvironmentsResponse
import io.confluent.intellijplugin.ccloud.model.response.ListClustersResponse
import io.confluent.intellijplugin.ccloud.model.response.ListSchemaRegistryResponse
import kotlinx.serialization.json.Json

/**
 * Implementation of ControlPlaneFetcher that makes REST API calls to Confluent Cloud control plane.
 *
 * @param client REST client configured with CONTROL_PLANE auth
 */
class ControlPlaneFetcherImpl(
    private val client: CCloudRestClient
) : ControlPlaneFetcher {

    // Prevent SerializationException when CCloud API adds new fields
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun getEnvironments(): List<Environment> {
        return client.fetchList(CloudConfig.ControlPlane.ENV_LIST_URI) { jsonBody ->
            val response = json.decodeFromString<ListEnvironmentsResponse>(jsonBody)
            val items = response.data.map { envData ->
                Environment(
                    id = envData.id,
                    displayName = envData.displayName ?: envData.id,
                    streamGovernancePackage = envData.streamGovernanceConfig?.packageName
                )
            }
            items to response.metadata?.next
        }
    }

    override suspend fun getKafkaClusters(envId: String): List<Cluster> {
        val uri = String.format(CloudConfig.ControlPlane.LKC_LIST_URI, envId)
        return client.fetchList(uri) { jsonBody ->
            val response = json.decodeFromString<ListClustersResponse>(jsonBody)
            val items = response.data.map { clusterData ->
                Cluster(
                    id = clusterData.id,
                    displayName = clusterData.displayName ?: clusterData.spec?.displayName ?: clusterData.id,
                    cloudProvider = clusterData.spec?.cloud ?: "Unknown",
                    region = clusterData.spec?.region ?: "Unknown",
                    httpEndpoint = clusterData.spec?.httpEndpoint ?: ""
                )
            }
            items to response.metadata?.next
        }
    }

    override suspend fun getSchemaRegistry(envId: String): SchemaRegistry? {
        val uri = String.format(CloudConfig.ControlPlane.SR_LIST_URI, envId)
        val registries = client.fetchList(uri) { jsonBody ->
            val response = json.decodeFromString<ListSchemaRegistryResponse>(jsonBody)
            val items = response.data.map { srData ->
                SchemaRegistry(
                    id = srData.id,
                    displayName = srData.spec?.displayName ?: srData.id,
                    cloudProvider = srData.spec?.cloud ?: "Unknown",
                    region = srData.spec?.region ?: "Unknown",
                    httpEndpoint = srData.spec?.httpEndpoint ?: ""
                )
            }
            items to response.metadata?.next
        }
        return registries.firstOrNull()
    }
}
