package io.confluent.intellijplugin.ccloud.cache

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import io.confluent.intellijplugin.ccloud.client.CCloudRestClient
import io.confluent.intellijplugin.ccloud.fetcher.DataPlaneFetcherImpl
import io.confluent.intellijplugin.ccloud.model.Cluster
import io.confluent.intellijplugin.ccloud.model.SchemaRegistry
import io.confluent.intellijplugin.ccloud.model.response.CreateTopicRequest
import io.confluent.intellijplugin.ccloud.model.response.SubjectData
import io.confluent.intellijplugin.ccloud.model.response.TopicData
import io.confluent.intellijplugin.ccloud.model.restEndpoint
import kotlinx.coroutines.*

/**
 * Data plane cache for cluster-level resources (topics, subjects, consumer groups).
 * One cache instance per cluster. Call refresh*() methods to populate/update cache.
 */
class DataPlaneCache(
    private val cluster: Cluster,
    private val schemaRegistry: SchemaRegistry?
) : Disposable {

    private var fetcher: DataPlaneFetcherImpl? = null
    private var kafkaClient: CCloudRestClient? = null

    // Cached data
    private var cachedTopics: List<TopicData>? = null
    private var cachedSubjects: List<SubjectData>? = null

    companion object {
        private const val ENRICHMENT_TIMEOUT_MS = 15_000L // 15 seconds
    }

    fun connect() {
        thisLogger().info("Connecting DataPlaneCache for cluster ${cluster.id}")
        val kafka = CCloudRestClient(
            baseUrl = cluster.restEndpoint,
            authType = CCloudRestClient.AuthType.DATA_PLANE
        )
        val srClient = if (schemaRegistry != null) {
            CCloudRestClient(
                baseUrl = schemaRegistry.httpEndpoint.removeSuffix(":443"),
                authType = CCloudRestClient.AuthType.DATA_PLANE,
                additionalHeaders = mapOf("target-sr-cluster" to schemaRegistry.id)
            )
        } else null

        kafkaClient = kafka
        fetcher = DataPlaneFetcherImpl(
            kafkaClient = kafka,
            schemaRegistryClient = srClient,
            clusterId = cluster.id,
            schemaRegistryId = schemaRegistry?.id
        )
        thisLogger().info("DataPlaneCache connected for cluster ${cluster.id}")
    }

    /** Get cached topics (empty if not loaded). */
    fun getTopics(): List<TopicData> = cachedTopics ?: emptyList()

    /** Fetch topics from API and update cache. */
    fun refreshTopics(): List<TopicData> {
        val topics = fetcher?.let { f ->
            runBlocking { f.listTopics() }
        } ?: emptyList()
        cachedTopics = topics
        return topics
    }

    /** Check if this cache has Schema Registry configured. */
    fun hasSchemaRegistry(): Boolean = schemaRegistry != null

    /** Get cached subjects (empty if not loaded). */
    fun getSubjects(): List<SubjectData> = cachedSubjects ?: emptyList()

    /** Fetch subjects from API and update cache. */
    fun refreshSubjects(): List<SubjectData> {
        if (schemaRegistry == null) return emptyList()

        val subjects = fetcher?.let { f ->
            runBlocking { f.listSubjectsWithDetails() }
        } ?: emptyList()
        cachedSubjects = subjects
        return subjects
    }

    /** Enrich topics with message count. */
    suspend fun enrichTopicsData(topics: List<TopicData>): Map<String, TopicEnrichmentData> = coroutineScope {
        thisLogger().info("Starting enrichment for ${topics.size} topics")

        val results = topics.map { topic ->
            async {
                try {
                    val messageCount = withTimeout(ENRICHMENT_TIMEOUT_MS) {
                        fetcher?.getTopicMessageCount(topic.topicName)
                    }

                    thisLogger().info("Enriched ${topic.topicName}: messageCount=$messageCount")

                    topic.topicName to TopicEnrichmentData(
                        messageCount = messageCount
                    )
                } catch (e: Exception) {
                    thisLogger().warn("Failed to enrich topic ${topic.topicName}: ${e.message}")
                    topic.topicName to TopicEnrichmentData()
                }
            }
        }.awaitAll().toMap()

        thisLogger().info("Enrichment completed: ${results.size} topics enriched")
        results
    }

    /** Create a new topic. */
    suspend fun createTopic(request: CreateTopicRequest): TopicData {
        return fetcher?.createTopic(request)
            ?: throw IllegalStateException("DataPlaneCache not connected for cluster ${cluster.id}")
    }

    /** Delete a topic. */
    suspend fun deleteTopic(topicName: String) {
        fetcher?.deleteTopic(topicName)
            ?: throw IllegalStateException("DataPlaneCache not connected for cluster ${cluster.id}")
    }

    override fun dispose() {
        kafkaClient = null
        fetcher = null
        cachedTopics = null
        cachedSubjects = null
    }
}
