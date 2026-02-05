package io.confluent.intellijplugin.data

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import io.confluent.intellijplugin.ccloud.cache.DataPlaneCache
import io.confluent.intellijplugin.ccloud.model.Cluster
import io.confluent.intellijplugin.ccloud.model.response.CreateTopicRequest
import io.confluent.intellijplugin.ccloud.model.response.TopicData
import io.confluent.intellijplugin.ccloud.model.response.toPresentable
import io.confluent.intellijplugin.client.KafkaConstants
import io.confluent.intellijplugin.core.monitoring.data.MonitoringDataManager
import io.confluent.intellijplugin.core.monitoring.data.model.ObjectDataModel
import io.confluent.intellijplugin.core.monitoring.data.updater.BdtMonitoringUpdater
import io.confluent.intellijplugin.core.monitoring.data.storage.RootDataModelStorage
import io.confluent.intellijplugin.core.monitoring.data.storage.ObjectDataModelStorage
import io.confluent.intellijplugin.core.util.invokeLater
import io.confluent.intellijplugin.model.BdtTopicPartition
import io.confluent.intellijplugin.model.ConsumerGroupOffsetInfo
import io.confluent.intellijplugin.model.ConsumerGroupPresentable
import io.confluent.intellijplugin.model.TopicConfig
import io.confluent.intellijplugin.model.TopicPresentable
import io.confluent.intellijplugin.registry.common.KafkaSchemaInfo
import io.confluent.intellijplugin.registry.KafkaRegistryType
import io.confluent.intellijplugin.rfs.ConfluentConnectionData
import io.confluent.intellijplugin.toolwindow.config.KafkaToolWindowSettings
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Cluster-level data manager for Confluent Cloud using the CCloud REST API.
 *
 * Uses [DataPlaneCache] for cluster resources (topics, partitions, configs) instead of Kafka AdminClient.
 * Does not support clearing topics/partitions or ISR data (CCloud API limitations).
 *
 * @param project The IDE project
 * @param orgManager Parent manager for the Confluent Cloud organization
 * @param cluster The specific cluster this manager is scoped to
 */
class CCloudClusterDataManager(
    project: Project?,
    private val orgManager: CCloudOrgManager,
    private val cluster: Cluster
) : BaseClusterDataManager(
    project,
    orgManager.settings,
    { orgManager.driver }
) {

    private val dataPlaneCache: DataPlaneCache = orgManager.getDataPlaneCache(cluster)

    override val connectionId: String = cluster.id

    override val connectionData: ConfluentConnectionData
        get() = orgManager.connectionData

    override val client
        get() = orgManager.client

    override val registryType: KafkaRegistryType
        get() = KafkaRegistryType.NONE

    init {
        RootDataModelStorage(updater, listOf(topicModel)).also { Disposer.register(this, it) }
    }

    override fun createTopicPartitionsStorage() = ObjectDataModelStorage<String, BdtTopicPartition>(
        updater,
        BdtTopicPartition::partitionId
    ) { topicName ->
        try {
            // Load basic partition info immediately
            val quickPartitions = runBlocking(Dispatchers.IO) {
                dataPlaneCache.getTopicPartitionsQuick(topicName)
            }

            if (quickPartitions.isEmpty()) return@ObjectDataModelStorage quickPartitions

            // Enrich with offsets progressively in background and unblock UI with loading state
            driver.coroutineScope.launch(Dispatchers.IO) {
                dataPlaneCache.enrichPartitionsProgressively(topicName, quickPartitions)
                    .collect { enrichedPartition ->
                        val storage = topicPartitionsModels[topicName] ?: return@collect
                        val current = storage.data ?: return@collect
                        val updated = current.map { p ->
                            if (p.partitionId == enrichedPartition.partitionId) enrichedPartition else p
                        }
                        withContext(Dispatchers.Default) {
                            storage.setData(updated)
                        }
                    }
            }

            quickPartitions
        } catch (e: Exception) {
            thisLogger().warn("Failed to load partitions for topic '$topicName' in cluster ${cluster.id}", e)
            emptyList()
        }
    }

    override suspend fun loadTopics(): List<TopicPresentable> = withContext(Dispatchers.IO) {
        try {
            dataPlaneCache.refreshTopics().map { it.toPresentable() }
        } catch (t: Throwable) {
            thisLogger().warn("Failed to load topics for cluster ${cluster.id}", t)
            emptyList()
        }
    }

    override suspend fun loadDetailedTopicsInfo(
        topics: List<TopicPresentable>
    ): Pair<List<TopicPresentable>, Throwable?> = withContext(Dispatchers.IO) {
        if (topics.isEmpty()) {
            return@withContext topics to null
        }

        try {
            val topicDataList = dataPlaneCache.getTopics()
            if (topicDataList.isEmpty()) {
                return@withContext topics to null
            }

            val enrichmentMap = dataPlaneCache.enrichTopicsData(topicDataList)

            val enrichedTopics = topics.map { topic ->
                enrichmentMap[topic.name]?.let { enrichment ->
                    topic.copy(messageCount = enrichment.messageCount)
                } ?: topic
            }

            enrichedTopics to null
        } catch (t: Throwable) {
            thisLogger().warn("Failed to load detailed topic info for cluster ${cluster.id}", t)
            topics to t
        }
    }

    override suspend fun fetchTopicPartitions(topicName: String): List<BdtTopicPartition> =
        withContext(Dispatchers.IO) {
            dataPlaneCache.getTopicPartitions(topicName)
        }

    override suspend fun getTopicConfig(
        topicName: String,
        showFullConfig: Boolean
    ): List<TopicConfig> = withContext(Dispatchers.IO) {
        dataPlaneCache.getTopicConfigs(topicName, showFullConfig)
    }

    override suspend fun loadConsumerGroups(): List<ConsumerGroupPresentable> {
        // TODO: Implement when CCloud REST API supports consumer groups
        return emptyList()
    }

    override suspend fun listConsumerGroupOffsets(consumerGroup: String): List<ConsumerGroupOffsetInfo> {
        // TODO: Implement when CCloud REST API supports consumer group offsets
        return emptyList()
    }

    override suspend fun listSchemasNames(limit: Int?, filter: String?): Pair<List<KafkaSchemaInfo>, Boolean> {
        // TODO: Implement when CCloud REST API supports schema registry
        return emptyList<KafkaSchemaInfo>() to false
    }

    override suspend fun updateSchemaList(
        schemas: List<KafkaSchemaInfo>
    ): Pair<List<KafkaSchemaInfo>, Throwable?> {
        // TODO: Implement when CCloud REST API supports schema registry
        return schemas to null
    }

    override suspend fun listSchemaVersions(schemaName: String): List<Long> {
        // TODO: Implement when CCloud REST API supports schema versions
        return emptyList()
    }

    override suspend fun createTopic(
        name: String,
        partitions: Int?,
        replicationFactor: Int?,
        configs: Map<String, String>
    ): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                val request = CreateTopicRequest(
                    topicName = name,
                    partitionsCount = partitions ?: KafkaConstants.DEFAULT_CCLOUD_PARTITION_COUNT,
                    replicationFactor = replicationFactor,
                    configs = configs.map { (k, v) ->
                        CreateTopicRequest.ConfigEntry(k, v)
                    }.ifEmpty { null }
                )
                dataPlaneCache.createTopic(request)
            }

            val newTopic = withContext(Dispatchers.IO) {
                dataPlaneCache.getTopics().find { it.topicName == name }?.toPresentable()
            }

            if (newTopic != null) {
                withContext(Dispatchers.Default) {
                    val currentTopics = topicModel.data ?: emptyList()
                    val config = KafkaToolWindowSettings.getInstance().getOrCreateConfig(connectionId)
                    val enrichedTopic = newTopic.copy(isFavorite = config.topicsPinned.contains(name))

                    val updatedTopics = (currentTopics + enrichedTopic).sortedWith(
                        compareByDescending<TopicPresentable> { it.isFavorite }
                            .thenBy { it.name.lowercase() }
                    )

                    topicModel.setData(updatedTopics)
                }
            } else {
                thisLogger().warn("Created topic '$name' not found in cache, triggering full refresh")
                invokeLater { updater.invokeRefreshModel(topicModel) }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            thisLogger().warn("Failed to create topic '$name'", e)
            invokeLater { updater.invokeRefreshModel(topicModel) }
            Result.failure(e)
        }
    }

    override suspend fun deleteTopic(topicNames: List<String>): Result<Unit> {
        return try {
            if (topicNames.isEmpty()) return Result.success(Unit)

            withContext(Dispatchers.IO) {
                topicNames.forEach { topicName ->
                    dataPlaneCache.deleteTopic(topicName)
                }
            }

            withContext(Dispatchers.Default) {
                val currentTopics = topicModel.data ?: emptyList()
                val updatedTopics = currentTopics.filterNot { it.name in topicNames }
                topicModel.setData(updatedTopics)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            thisLogger().warn("Failed to delete topics: $topicNames", e)
            invokeLater { updater.invokeRefreshModel(topicModel) }
            Result.failure(e)
        }
    }

    override suspend fun clearTopic(topicName: String): Result<Unit> {
        return Result.failure(UnsupportedOperationException("Clear topic not supported for Confluent Cloud"))
    }

    override fun clearPartitions(partitions: List<BdtTopicPartition>) {}

    override fun supportsClearPartitions(): Boolean = false

    override fun supportsInSyncReplicasData(): Boolean = false

    override fun dispose() {}
}
