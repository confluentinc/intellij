package io.confluent.intellijplugin.ccloud.fetcher

import io.confluent.intellijplugin.ccloud.client.DataPlaneRestClient
import io.confluent.intellijplugin.ccloud.config.CloudConfig
import io.confluent.intellijplugin.ccloud.model.response.ListTopicsResponse
import io.confluent.intellijplugin.ccloud.model.response.TopicData
import kotlinx.serialization.json.Json

/**
 * Kafka cluster data plane operations via REST API v3.
 *
 * Supports ALL operations via REST API:
 * - Topics: List, create, delete, describe
 * - Partitions: List, describe
 * - Configs: List, update
 * - Consumer Groups: List, describe
 * - Records: Produce, consume (via REST)
 * - Message Counts: Get offsets (internal API)
 *
 * @param client Data plane REST client for this cluster
 * @param clusterId Kafka cluster ID (lkc-xxxxx)
 * @see <a href="https://docs.confluent.io/cloud/current/api.html#tag/Topic-(v3)">Kafka REST API v3</a>
 */
class ClusterFetcherImpl(
    private val client: DataPlaneRestClient,
    private val clusterId: String
) : ClusterFetcher {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * List all topics in the cluster.
     *
     * @return List of topics in the cluster
     * @see <a href="https://docs.confluent.io/cloud/current/api.html#tag/Topic-(v3)/operation/listKafkaTopics">List Topics</a>
     */
    override suspend fun listTopics(): List<TopicData> {
        val path = String.format(CloudConfig.DataPlane.Kafka.TOPICS_URI, clusterId)

        return client.fetchList(path) { body ->
            val response = json.decodeFromString<ListTopicsResponse>(body)
            response.data to response.metadata.next
        }
    }
}
