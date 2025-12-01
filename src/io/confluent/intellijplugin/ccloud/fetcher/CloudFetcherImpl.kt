package io.confluent.intellijplugin.ccloud.fetcher

import io.confluent.intellijplugin.ccloud.client.CloudRestClient
import io.confluent.intellijplugin.ccloud.config.CloudConfig
import io.confluent.intellijplugin.ccloud.model.*

/**
 * Implementation of CloudFetcher that makes REST API calls to Confluent Cloud control plane.
 */
class CloudFetcherImpl(
    apiKey: String,
    apiSecret: String,
    baseUrl: String = CloudConfig.CONTROL_PLANE_BASE_URL
) : CloudRestClient(apiKey, apiSecret, baseUrl), CloudFetcher {

    override suspend fun getKafkaClusters(
        connectionId: String,
        envId: String
    ): List<KafkaCluster> {
        TODO("Not yet implemented")
    }

    override suspend fun getSchemaRegistry(connectionId: String, envId: String): SchemaRegistry? {
        TODO("Not yet implemented")
    }
}

