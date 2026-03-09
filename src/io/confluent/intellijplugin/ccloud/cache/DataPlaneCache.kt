package io.confluent.intellijplugin.ccloud.cache

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import io.confluent.intellijplugin.ccloud.client.CCloudRestClient
import io.confluent.intellijplugin.ccloud.fetcher.DataPlaneFetcherImpl
import io.confluent.intellijplugin.ccloud.model.Cluster
import io.confluent.intellijplugin.ccloud.model.SchemaRegistry
import io.confluent.intellijplugin.ccloud.model.response.CreateTopicRequest
import io.confluent.intellijplugin.ccloud.model.response.SchemaData
import io.confluent.intellijplugin.ccloud.model.response.SchemaEnrichmentData
import io.confluent.intellijplugin.ccloud.model.response.SchemaEnrichmentResult
import io.confluent.intellijplugin.ccloud.model.response.TopicData
import io.confluent.intellijplugin.ccloud.model.response.TopicEnrichmentData
import io.confluent.intellijplugin.ccloud.model.response.TopicEnrichmentResult
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
 * Concurrency limit for schema/topic enrichment operations.
 * Matches CCloud API rate limit (5 req/sec) to maximize throughput while staying within limits.
 */
private const val ENRICHMENT_CONCURRENCY = 5

/**
 * Concurrency limit for partition offset enrichment.
 * Conservative (each partition = 2 API calls) to allow multiple topics loading simultaneously.
 */
private const val PARTITION_ENRICHMENT_CONCURRENCY = 2

/**
 * Data plane cache for cluster resources (topics, schemas, consumer groups).
 * One cache per cluster. Call refresh*() to populate/update cache.
 */
class DataPlaneCache(
    private val cluster: Cluster,
    private val schemaRegistry: SchemaRegistry?
) : Disposable {

    private var fetcher: DataPlaneFetcherImpl? = null
    private var kafkaClient: CCloudRestClient? = null

    private var cachedTopics: List<TopicData>? = null
    private var cachedSchemas: List<SchemaData>? = null

    companion object {
        private const val ENRICHMENT_TIMEOUT_MS = 30_000L
    }

    fun connect() {
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
        thisLogger().info("Connected DataPlaneCache for cluster ${cluster.id}")
    }

    fun getFetcher(): DataPlaneFetcherImpl? = fetcher

    fun getTopics(): List<TopicData> = cachedTopics ?: emptyList()

    suspend fun refreshTopics(): List<TopicData> {
        val topics = fetcher?.getTopics() ?: emptyList()
        cachedTopics = topics
        return topics
    }

    fun hasSchemaRegistry(): Boolean = schemaRegistry != null

    fun getSchemaRegistryId(): String? = schemaRegistry?.id

    fun getSchemas(): List<SchemaData> = cachedSchemas ?: emptyList()

    suspend fun refreshSchemas(): List<SchemaData> {
        if (schemaRegistry == null) return emptyList()

        val subjectNames = fetcher?.getAllSubjects() ?: emptyList()
        val existingByName = cachedSchemas?.associateBy { it.name } ?: emptyMap()
        val schemas = subjectNames.map { name ->
            existingByName[name] ?: SchemaData(name = name)
        }
        cachedSchemas = schemas
        return schemas
    }

    fun updateSchemaInCache(schemaName: String, enrichmentData: SchemaEnrichmentData) {
        cachedSchemas = cachedSchemas?.map { schema ->
            if (schema.name == schemaName) {
                schema.copy(
                    latestVersion = enrichmentData.latestVersion,
                    schemaType = enrichmentData.schemaType,
                    compatibility = enrichmentData.compatibility
                )
            } else {
                schema
            }
        }
    }

    fun enrichSchemasProgressively(schemas: List<SchemaData>): Flow<SchemaEnrichmentResult> = channelFlow {
        if (fetcher == null) return@channelFlow

        thisLogger().info("Starting progressive enrichment for ${schemas.size} schemas (max $ENRICHMENT_CONCURRENCY concurrent)")
        val completed = AtomicInteger(0)
        val semaphore = kotlinx.coroutines.sync.Semaphore(ENRICHMENT_CONCURRENCY)

        schemas.forEach { schema ->
            launch {
                semaphore.acquire()
                try {
                    val info = withTimeout(ENRICHMENT_TIMEOUT_MS) {
                        fetcher?.loadSchemaInfo(schema.name)
                    }

                    val count = completed.incrementAndGet()
                    send(
                        SchemaEnrichmentResult.Success(
                            schemaName = schema.name,
                            data = SchemaEnrichmentData(
                                latestVersion = info?.latestVersion,
                                schemaType = info?.schemaType,
                                compatibility = info?.compatibility
                            ),
                            progress = count to schemas.size
                        )
                    )

                    thisLogger().debug("Enriched ${schema.name}: version=${info?.latestVersion}, type=${info?.schemaType}, compatibility=${info?.compatibility} ($count/${schemas.size})")
                } catch (e: Exception) {
                    val count = completed.incrementAndGet()
                    send(
                        SchemaEnrichmentResult.Failure(
                            schemaName = schema.name,
                            progress = count to schemas.size,
                            error = e
                        )
                    )

                    thisLogger().debug("Failed to enrich ${schema.name}: ${e.message} ($count/${schemas.size})")
                } finally {
                    semaphore.release()
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Enrich schemas with metadata, blocking until all complete.
     */
    suspend fun enrichSchemas(schemas: List<SchemaData>): Map<String, SchemaEnrichmentData> {
        if (fetcher == null) return emptyMap()

        return coroutineScope {
            thisLogger().info("Starting enrichment for ${schemas.size} schemas")

            val results = schemas.map { schema ->
                async {
                    try {
                        val info = withTimeout(ENRICHMENT_TIMEOUT_MS) {
                            fetcher?.loadSchemaInfo(schema.name)
                        }

                    thisLogger().debug("Enriched ${schema.name}: version=${info?.latestVersion}, type=${info?.schemaType}, compatibility=${info?.compatibility}")

                    schema.name to SchemaEnrichmentData(
                        latestVersion = info?.latestVersion,
                        schemaType = info?.schemaType,
                        compatibility = info?.compatibility
                    )
                } catch (e: Exception) {
                    thisLogger().warn("Failed to enrich ${schema.name}: ${e.message}")
                    schema.name to SchemaEnrichmentData()
                }
            }
        }.awaitAll().toMap()

            thisLogger().info("Enrichment completed: ${results.size} schemas enriched")
            results
        }
    }

    fun enrichTopicsDataProgressively(topics: List<TopicData>): Flow<TopicEnrichmentResult> = channelFlow {
        if (fetcher == null) return@channelFlow

        thisLogger().info("Starting progressive enrichment for ${topics.size} topics (max $ENRICHMENT_CONCURRENCY concurrent)")
        val completed = AtomicInteger(0)
        val semaphore = kotlinx.coroutines.sync.Semaphore(ENRICHMENT_CONCURRENCY)

        topics.forEach { topic ->
            launch {
                semaphore.acquire()
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

                    thisLogger().debug("Enriched ${topic.topicName}: messageCount=$messageCount ($count/${topics.size})")
                } catch (e: Exception) {
                    val count = completed.incrementAndGet()
                    send(
                        TopicEnrichmentResult.Failure(
                            topicName = topic.topicName,
                            progress = count to topics.size,
                            error = e
                        )
                    )

                    thisLogger().debug("Failed to enrich topic ${topic.topicName}: ${e.message} ($count/${topics.size})")
                } finally {
                    semaphore.release()
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Enrich topics with message count, blocking until all complete.
     */
    suspend fun enrichTopicsData(topics: List<TopicData>): Map<String, TopicEnrichmentData> {
        if (fetcher == null) return emptyMap()

        return coroutineScope {
            thisLogger().info("Starting enrichment for ${topics.size} topics")

            val results = topics.map { topic ->
                async {
                    try {
                        val messageCount = withTimeout(ENRICHMENT_TIMEOUT_MS) {
                            fetcher?.getTopicMessageCount(topic.topicName)
                        }

                    thisLogger().debug("Enriched ${topic.topicName}: messageCount=$messageCount")

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
    }

    suspend fun createTopic(request: CreateTopicRequest): TopicData {
        return fetcher?.createTopic(request)
            ?: throw IllegalStateException("DataPlaneCache not connected for cluster ${cluster.id}")
    }

    suspend fun deleteTopic(topicName: String) {
        fetcher?.deleteTopic(topicName)
            ?: throw IllegalStateException("DataPlaneCache not connected for cluster ${cluster.id}")
    }

    /** Get topic partitions without offsets. Use enrichPartitionsProgressively() for offsets. */
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

            val semaphore = kotlinx.coroutines.sync.Semaphore(PARTITION_ENRICHMENT_CONCURRENCY)

            partitions.forEach { partition ->
                launch {
                    semaphore.acquire()
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
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        thisLogger().warn("Failed to fetch offsets for $topicName/${partition.partitionId}: ${e.message}")
                        send(partition)
                    } finally {
                        semaphore.release()
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
        val configs = f.getTopicConfig(topicName)

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
