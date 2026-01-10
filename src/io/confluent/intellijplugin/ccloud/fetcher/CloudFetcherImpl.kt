package io.confluent.intellijplugin.ccloud.fetcher

import io.confluent.intellijplugin.ccloud.auth.CCloudAuthService
import io.confluent.intellijplugin.ccloud.client.ControlPlaneRestClient
import io.confluent.intellijplugin.ccloud.config.CloudConfig
import io.confluent.intellijplugin.ccloud.model.*
import io.confluent.intellijplugin.ccloud.model.response.ListEnvironmentsResponse
import io.confluent.intellijplugin.ccloud.model.response.ListClustersResponse
import io.confluent.intellijplugin.ccloud.model.response.ListSchemaRegistryResponse
import kotlinx.serialization.json.Json

/**
 * Implementation of CloudFetcher that makes REST API calls to Confluent Cloud control plane.
 * Uses control plane OAuth token for resource discovery (environments, clusters, schema registries).
 */
class CloudFetcherImpl(
    baseUrl: String = CloudConfig.CONTROL_PLANE_BASE_URL,
    private val authService: CCloudAuthService? = null
) : ControlPlaneRestClient(baseUrl), CloudFetcher {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Gets authentication headers for API requests.
     * Uses injected authService if provided (for testing), otherwise uses singleton instance.
     */
    override fun getAuthHeaders(): Map<String, String> {
        val service = authService ?: CCloudAuthService.getInstance()

        if (!service.isSignedIn()) {
            throw IllegalStateException("Not signed in to Confluent Cloud")
        }

        val token = service.getControlPlaneToken()
            ?: throw IllegalStateException("No control plane token available")

        return mapOf(
            "Authorization" to "Bearer $token",
            "Content-Type" to "application/json"
        )
    }

    override suspend fun getEnvironments(): List<Environment> {
        val headers = getAuthHeaders()
        return listItems(headers, CloudConfig.ControlPlane.ENV_LIST_URI) { jsonBody ->
            val response = json.decodeFromString<ListEnvironmentsResponse>(jsonBody)
            val items = response.data.map { envData ->
                Environment(
                    id = envData.id,
                    displayName = envData.displayName ?: envData.id
                )
            }
            items to response.metadata?.next
        }
    }

    override suspend fun getKafkaClusters(envId: String): List<Cluster> {
        val headers = getAuthHeaders()
        val uri = String.format(CloudConfig.ControlPlane.LKC_LIST_URI, envId)
        return listItems(headers, uri) { jsonBody ->
            val response = json.decodeFromString<ListClustersResponse>(jsonBody)
            val items = response.data.map { clusterData ->
                Cluster(
                    id = clusterData.id,
                    displayName = clusterData.displayName ?: clusterData.id,
                    cloudProvider = clusterData.spec?.cloud ?: "Unknown",
                    region = clusterData.spec?.region ?: "Unknown",
                    httpEndpoint = clusterData.spec?.httpEndpoint ?: ""
                )
            }
            items to response.metadata?.next
        }
    }

    override suspend fun getSchemaRegistry(envId: String): List<SchemaRegistry> {
        val headers = getAuthHeaders()
        val uri = String.format(CloudConfig.ControlPlane.SR_LIST_URI, envId)
        return listItems(headers, uri) { jsonBody ->
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
    }
}

