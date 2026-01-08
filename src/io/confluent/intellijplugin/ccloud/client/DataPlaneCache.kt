package io.confluent.intellijplugin.ccloud.client

import com.intellij.openapi.Disposable
import io.confluent.intellijplugin.ccloud.fetcher.DataPlaneFetcherImpl
import io.confluent.intellijplugin.ccloud.model.Cluster
import io.confluent.intellijplugin.ccloud.model.SchemaRegistry
import io.confluent.intellijplugin.ccloud.model.response.SubjectData
import io.confluent.intellijplugin.ccloud.model.response.TopicData
import io.confluent.intellijplugin.ccloud.model.restEndpoint
import kotlinx.coroutines.runBlocking

/**
 * Data plane cache for cluster-level resources (topics, subjects, consumer groups).
 *
 * One cache instance per cluster. Call refresh*() methods to populate/update cache.
 *
 * @param cluster Kafka cluster to fetch data for
 * @param schemaRegistry Schema Registry for this cluster's environment (optional)
 */
class DataPlaneCache(
    private val cluster: Cluster,
    private val schemaRegistry: SchemaRegistry?
) : Disposable {

    private var fetcher: DataPlaneFetcherImpl? = null

    // Cached data
    private var cachedTopics: List<TopicData>? = null
    private var cachedSubjects: List<SubjectData>? = null
    /** Initialize fetcher (must be called before get/refresh operations). */
    fun connect() {
        val kafkaClient = DataPlaneRestClient(cluster.restEndpoint)
        val srClient = if (schemaRegistry != null) {
            DataPlaneRestClient(
                baseUrl = schemaRegistry.httpEndpoint.removeSuffix(":443"),
                additionalHeaders = mapOf("target-sr-cluster" to schemaRegistry.id)
            )
        } else null

        fetcher = DataPlaneFetcherImpl(
            kafkaClient = kafkaClient,
            schemaRegistryClient = srClient,
            clusterId = cluster.id,
            schemaRegistryId = schemaRegistry?.id
        )
    }

    // ========== Topics ==========

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

    // ========== Schema Registry ==========

    /** Get cached subjects (empty if not loaded or no Schema Registry). */
    fun getSubjects(): List<SubjectData> = cachedSubjects ?: emptyList()

    /** Fetch subjects from Schema Registry and update cache. */
    fun refreshSubjects(): List<SubjectData> {
        if (schemaRegistry == null) return emptyList()
        val subjects = fetcher?.let { f ->
            runBlocking { f.listSubjectsWithDetails() }
        } ?: emptyList()
        cachedSubjects = subjects
        return subjects
    }

    /** Check if Schema Registry is available. */
    fun hasSchemaRegistry(): Boolean = schemaRegistry != null

    // ========== Cache Management ==========

    /** Clear all cached data (does not dispose fetcher). */
    fun clearCache() {
        cachedTopics = null
        cachedSubjects = null
    }

    override fun dispose() {
        fetcher = null
        clearCache()
    }

    fun getClusterId(): String = cluster.id
    fun getClusterName(): String = cluster.displayName
}
