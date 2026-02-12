package io.confluent.intellijplugin.ccloud.cache

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import io.confluent.intellijplugin.ccloud.client.CCloudRestClient
import io.confluent.intellijplugin.ccloud.fetcher.DataPlaneFetcherImpl
import io.confluent.intellijplugin.ccloud.model.Cluster
import io.confluent.intellijplugin.ccloud.model.SchemaRegistry
import io.confluent.intellijplugin.ccloud.model.response.*
import io.confluent.intellijplugin.ccloud.model.restEndpoint
import io.confluent.intellijplugin.client.KafkaConstants.DEFAULT_CCLOUD_REPLICATION_FACTOR
import io.confluent.intellijplugin.model.BdtTopicPartition
import io.confluent.intellijplugin.model.TopicConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import java.util.concurrent.atomic.AtomicInteger

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
    private var cachedSchemas: List<SchemaData>? = null

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
                // Required for OAuth/bearer token auth with CCloud multi-tenant SR endpoints.
                // Routes request to specific SR cluster since data plane token is not cluster-specific.
                // See: https://docs.confluent.io/cloud/current/sr/sr-rest-apis.html#oauth-for-ccloud-sr-rest-api
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

    /** Get the data plane fetcher for API operations. */
    fun getFetcher(): DataPlaneFetcherImpl? = fetcher

    /** Get cached topics (empty if not loaded). */
    fun getTopics(): List<TopicData> = cachedTopics ?: emptyList()

    /** Fetch topics from API and update cache. */
    suspend fun refreshTopics(): List<TopicData> {
        val topics = fetcher?.listTopics() ?: emptyList()
        cachedTopics = topics
        return topics
    }

    /** Check if this cache has Schema Registry configured. */
    fun hasSchemaRegistry(): Boolean = schemaRegistry != null

    /** Get cached schemas (empty if not loaded). */
    fun getSchemas(): List<SchemaData> = cachedSchemas ?: emptyList()

    /** Fetch schemas from API and update cache. Fast initial load (names only). */
    fun refreshSchemas(): List<SchemaData> {
        if (schemaRegistry == null) return emptyList()

        // Fast initial load: fetch names only, enrichment happens separately
        // runBlocking required: called from non-suspend doLoadChildren() but needs to call suspend functions
        val schemaNames = runBlocking { fetcher?.listSchemas() } ?: emptyList()
        val schemas = schemaNames.map { SchemaData(name = it) }
        cachedSchemas = schemas
        return schemas
    }

    /**
     * Enrich schemas with version/type/compatibility progressively.
     * Emits results one by one as they complete, allowing UI to update incrementally.
     */
    fun enrichSchemasDataProgressively(schemas: List<SchemaData>): Flow<SchemaEnrichmentResult> = channelFlow {
        thisLogger().info("Starting progressive enrichment for ${schemas.size} schemas")
        val completed = AtomicInteger(0)

        schemas.forEach { schema ->
            launch {
                try {
                    val enrichedSchema = withTimeout(ENRICHMENT_TIMEOUT_MS) {
                        fetcher?.loadSchemaInfo(schema.name)
                    }

                    val count = completed.incrementAndGet()
                    send(
                        SchemaEnrichmentResult.Success(
                            schemaName = schema.name,
                            data = SchemaEnrichmentData(
                                latestVersion = enrichedSchema?.latestVersion,
                                schemaType = enrichedSchema?.schemaType,
                                compatibility = enrichedSchema?.compatibility
                            ),
                            progress = count to schemas.size
                        )
                    )

                    thisLogger().info("Enriched ${schema.name}: version=${enrichedSchema?.latestVersion}, type=${enrichedSchema?.schemaType} ($count/${schemas.size})")
                } catch (e: Exception) {
                    val count = completed.incrementAndGet()
                    send(
                        SchemaEnrichmentResult.Failure(
                            schemaName = schema.name,
                            progress = count to schemas.size,
                            error = e
                        )
                    )

                    thisLogger().warn("Failed to enrich schema ${schema.name}: ${e.message} ($count/${schemas.size})")
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Enrich schemas with version/type/compatibility (blocking until all complete).
     */
    suspend fun enrichSchemasData(schemas: List<SchemaData>): Map<String, SchemaEnrichmentData> = coroutineScope {
        thisLogger().info("Starting enrichment for ${schemas.size} schemas")

        val results = schemas.map { schema ->
            async {
                try {
                    val enrichedSchema = withTimeout(ENRICHMENT_TIMEOUT_MS) {
                        fetcher?.loadSchemaInfo(schema.name)
                    }

                    thisLogger().info("Enriched ${schema.name}: version=${enrichedSchema?.latestVersion}, type=${enrichedSchema?.schemaType}")

                    schema.name to SchemaEnrichmentData(
                        latestVersion = enrichedSchema?.latestVersion,
                        schemaType = enrichedSchema?.schemaType,
                        compatibility = enrichedSchema?.compatibility
                    )
                } catch (e: Exception) {
                    thisLogger().warn("Failed to enrich schema ${schema.name}: ${e.message}")
                    schema.name to SchemaEnrichmentData()
                }
            }
        }.awaitAll().toMap()

        thisLogger().info("Enrichment completed: ${results.size} schemas enriched")
        results
    }

    /**
     * Enrich topics with message count progressively.
     * Emits results one by one as they complete, allowing UI to update incrementally.
     */
    fun enrichTopicsDataProgressively(topics: List<TopicData>): Flow<TopicEnrichmentResult> = channelFlow {
        thisLogger().info("Starting progressive enrichment for ${topics.size} topics")
        val completed = AtomicInteger(0)

        topics.forEach { topic ->
            launch {
                try {
                    val messageCount = withTimeout(ENRICHMENT_TIMEOUT_MS) {
                        fetcher?.getTopicMessageCount(topic.topicName)
                    }

                    val count = completed.incrementAndGet()
                    send(
                        TopicEnrichmentResult.Success(
                            topicName = topic.topicName,
                            data = TopicEnrichmentData(messageCount = messageCount),
                            progress = count to topics.size
                        )
                    )

                    thisLogger().info("Enriched ${topic.topicName}: messageCount=$messageCount ($count/${topics.size})")
                } catch (e: Exception) {
                    val count = completed.incrementAndGet()
                    send(
                        TopicEnrichmentResult.Failure(
                            topicName = topic.topicName,
                            progress = count to topics.size,
                            error = e
                        )
                    )

                    thisLogger().warn("Failed to enrich topic ${topic.topicName}: ${e.message} ($count/${topics.size})")
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Enrich topics with message count (blocking until all complete).
     */
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

    /**
     * Get topic partitions without offsets (fast). 
     * Use enrichPartitionsProgressively() to load offsets incrementally.
     */
    suspend fun getTopicPartitionsQuick(topicName: String): List<BdtTopicPartition> {
        val f = fetcher ?: throw IllegalStateException("DataPlaneCache not connected")
        val topic = cachedTopics?.find { it.topicName == topicName }
        val replicationFactor = topic?.replicationFactor ?: DEFAULT_CCLOUD_REPLICATION_FACTOR

        val partitions = f.describeTopicPartitions(topicName)

        return partitions.map { partition ->
            BdtTopicPartition(
                topic = topicName,
                partitionId = partition.partitionId,
                leader = extractBrokerIdFromUrl(partition.leader?.related),
                internalReplicas = emptyList(),
                inSyncReplicasCount = replicationFactor,
                replicas = replicationFactor.toString(),
                endOffset = null,
                startOffset = null
            )
        }
    }

    fun enrichPartitionsProgressively(topicName: String, partitions: List<BdtTopicPartition>): Flow<BdtTopicPartition> =
        channelFlow {
            val f = fetcher ?: return@channelFlow

            partitions.forEach { partition ->
                launch {
                    try {
                        val startOffsetResponse =
                            f.getPartitionOffsets(topicName, partition.partitionId, fromBeginning = true)
                        val endOffsetResponse =
                            f.getPartitionOffsets(topicName, partition.partitionId, fromBeginning = false)

                        send(
                            partition.copy(
                                startOffset = startOffsetResponse.nextOffset,
                                endOffset = endOffsetResponse.nextOffset
                            )
                        )
                    } catch (e: Exception) {
                        thisLogger().warn("Failed to fetch offsets for $topicName/${partition.partitionId}: ${e.message}")
                        send(partition)
                    }
                }
            }
        }.flowOn(Dispatchers.IO)

    private fun extractBrokerIdFromUrl(url: String?): Int? {
        if (url.isNullOrEmpty()) return null
        return url.substringAfterLast("/", "").toIntOrNull()
    }

    /**
     * Get topic configs. Filters by showFullConfig setting.
     * Note: CCloud REST API returns configs alphabetically, unlike Kafka AdminClient which uses ConfigDef order.
     */
    suspend fun getTopicConfigs(topicName: String, showFullConfig: Boolean): List<TopicConfig> {
        val f = fetcher ?: throw IllegalStateException("DataPlaneCache not connected")
        val configs = f.describeTopicConfiguration(topicName)

        val filteredConfigs = if (showFullConfig) configs else configs.filter { !it.isDefault }

        return filteredConfigs.map { config ->
            TopicConfig(
                name = config.name,
                value = config.value ?: "",
                defaultValue = config.synonyms
                    ?.firstOrNull { it.source == "DEFAULT_CONFIG" }
                    ?.value ?: ""
            )
        }
    }

    override fun dispose() {
        kafkaClient = null
        fetcher = null
        cachedTopics = null
        cachedSchemas = null
    }
}
