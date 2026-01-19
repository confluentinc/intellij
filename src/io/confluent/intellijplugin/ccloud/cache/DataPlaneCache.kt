package io.confluent.intellijplugin.ccloud.cache

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import io.confluent.intellijplugin.ccloud.client.CCloudRestClient
import io.confluent.intellijplugin.ccloud.fetcher.DataPlaneFetcherImpl
import io.confluent.intellijplugin.ccloud.model.Cluster
import io.confluent.intellijplugin.ccloud.model.SchemaRegistry
import io.confluent.intellijplugin.ccloud.model.response.SubjectData
import io.confluent.intellijplugin.ccloud.model.response.TopicData
import io.confluent.intellijplugin.ccloud.model.restEndpoint
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking

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

    fun connect() {
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

    override fun dispose() {
        kafkaClient = null
        fetcher = null
        cachedTopics = null
        cachedSubjects = null
    }
}
