package io.confluent.intellijplugin.data

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import io.confluent.intellijplugin.ccloud.client.DataPlaneRestClient
import io.confluent.intellijplugin.ccloud.fetcher.ClusterFetcherImpl
import io.confluent.intellijplugin.ccloud.model.KafkaCluster
import io.confluent.intellijplugin.ccloud.model.response.toPresentable
import io.confluent.intellijplugin.ccloud.model.restEndpoint
import io.confluent.intellijplugin.model.TopicPresentable
import kotlinx.coroutines.runBlocking

/**
 * Manages data plane resources for a Kafka cluster via REST API.
 *
 * Use for: List/describe topics, configs.
 * For produce/consume: Use native Kafka protocol via KafkaClient.
 */
class ConfluentDataPlaneManager(
    private val project: Project?,
    private val cluster: KafkaCluster
) {
    private val log = Logger.getInstance(ConfluentDataPlaneManager::class.java)

    // REST client for simple operations (list, describe, configs)
    private val restClient: DataPlaneRestClient by lazy {
        DataPlaneRestClient(cluster.restEndpoint)
    }

    private val restFetcher: ClusterFetcherImpl by lazy {
        ClusterFetcherImpl(restClient, cluster.id)
    }

    /**
     * Get all topics for this cluster.
     *
     * @return List of topics as TopicPresentable for UI rendering
     */
    fun getTopics(): List<TopicPresentable> = runBlocking {
        try {
            restFetcher.listTopics().toPresentable()
        } catch (e: Exception) {
            log.warn("Failed to fetch topics for cluster ${cluster.id}: ${e.message}")
            emptyList()
        }
    }
}
