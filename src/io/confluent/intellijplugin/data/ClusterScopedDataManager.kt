package io.confluent.intellijplugin.data

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import io.confluent.intellijplugin.ccloud.cache.DataPlaneCache
import io.confluent.intellijplugin.ccloud.model.Cluster
import io.confluent.intellijplugin.ccloud.model.response.CreateTopicRequest
import io.confluent.intellijplugin.ccloud.model.response.TopicData
import io.confluent.intellijplugin.ccloud.model.response.toPresentable
import io.confluent.intellijplugin.core.monitoring.data.MonitoringDataManager
import io.confluent.intellijplugin.core.monitoring.data.model.ObjectDataModel
import io.confluent.intellijplugin.core.monitoring.data.updater.BdtMonitoringUpdater
import io.confluent.intellijplugin.core.monitoring.data.storage.RootDataModelStorage
import io.confluent.intellijplugin.core.monitoring.data.storage.ObjectDataModelStorage
import io.confluent.intellijplugin.core.util.invokeLater
import io.confluent.intellijplugin.model.TopicPresentable
import io.confluent.intellijplugin.model.BdtTopicPartition
import io.confluent.intellijplugin.model.TopicConfig
import io.confluent.intellijplugin.registry.KafkaRegistryType
import io.confluent.intellijplugin.rfs.ConfluentConnectionData
import io.confluent.intellijplugin.toolwindow.config.KafkaToolWindowSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Cluster-scoped wrapper around ConfluentDataManager that presents the interface
 * expected by TopicsController (designed for KafkaDataManager).
 *
 * This allows reuse of TopicsController for Confluent Cloud clusters.
 */
class ClusterScopedDataManager(
    project: Project?,
    private val confluentDataManager: ConfluentDataManager,
    private val cluster: Cluster
) : MonitoringDataManager(
    project,
    confluentDataManager.settings,
    { confluentDataManager.driver }
), TopicDataProvider, TopicOperations, TopicDetailDataProvider {

    private val dataPlaneCache: DataPlaneCache = confluentDataManager.getDataPlaneCache(cluster)

    override val connectionId: String = cluster.id

    override val connectionData: ConfluentConnectionData
        get() = confluentDataManager.connectionData

    override val topicModel: ObjectDataModel<TopicPresentable> = createTopicsDataModel().also {
        Disposer.register(this, it)
    }

    override val topicPartitionsModels = createTopicPartitionsStorage().also { Disposer.register(this, it) }
    override val topicConfigsModels = createTopicConfigsStorage().also { Disposer.register(this, it) }

    override val client
        get() = confluentDataManager.client

    override val registryType: KafkaRegistryType
        get() = KafkaRegistryType.NONE

    init {
        RootDataModelStorage(confluentDataManager.updater, listOf(topicModel))
    }

    /**
     * Get topics for this cluster.
     */
    override fun getTopics(): List<TopicPresentable> = topicModel.data ?: emptyList()

    /**
     * Create topic data model with enrichment (message count).
     */
    private fun createTopicsDataModel() = ObjectDataModel(
        idFieldName = TopicPresentable::name,
        additionalInfoLoading = { model ->
            val basicTopics = model.data ?: emptyList()
            val topicDataList = dataPlaneCache.getTopics()

            if (topicDataList.isEmpty()) {
                return@ObjectDataModel basicTopics to null
            }

            try {
                val enrichmentMap = dataPlaneCache.enrichTopicsData(topicDataList)

                val enrichedTopics = basicTopics.map { topic ->
                    enrichmentMap[topic.name]?.let { enrichment ->
                        topic.copy(
                            messageCount = enrichment.messageCount
                        )
                    } ?: topic
                }

                enrichedTopics to null
            } catch (t: Throwable) {
                thisLogger().warn("Failed to enrich topic data for cluster ${cluster.id}", t)
                basicTopics to t
            }
        }
    ) {
        val toolWindowSettings = KafkaToolWindowSettings.getInstance()
        val config = toolWindowSettings.getOrCreateConfig(connectionId)
        val topicFilterName = config.topicFilterName

        val topicDataList = dataPlaneCache.refreshTopics()

        val allTopics = topicDataList
            .filter { topicData ->
                val showInternal = toolWindowSettings.showInternalTopics
                (showInternal || !topicData.isInternal) &&
                (topicFilterName == null || topicData.topicName.lowercase().contains(topicFilterName.lowercase()))
            }
            .map { topicData ->
                val isFavorite = config.topicsPined.contains(topicData.topicName)
                topicData.toPresentable().copy(isFavorite = isFavorite)
            }

        // Filter favorites if needed, then sort: favorites first, then alphabetically
        val topics = if (toolWindowSettings.showFavoriteTopics) {
            allTopics.filter { it.isFavorite }
        } else {
            allTopics
        }.sortedWith(compareByDescending<TopicPresentable> { it.isFavorite }.thenBy { it.name.lowercase() })

        val topicLimit = config.topicLimit
        (topicLimit?.let { topics.take(it) } ?: topics) to (topicLimit != null && topics.size > topicLimit)
    }

    /**
     * Update favorite topics for this cluster.
     * Stores favorites per cluster ID.
     */
    override fun updatePinedTopics(topicName: String, isForAdding: Boolean) {
        val config = KafkaToolWindowSettings.getInstance().getOrCreateConfig(connectionId)
        if (isForAdding) {
            config.topicsPined += topicName
        } else {
            config.topicsPined -= topicName
        }
        confluentDataManager.updater.invokeRefreshModel(topicModel)
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
                    // Use 6 as default partition count (CCloud requirement)
                    partitionsCount = partitions ?: 6,
                    // Use 3 as default replication factor (CCloud default)
                    replicationFactor = replicationFactor,
                    configs = configs.map { (k, v) ->
                        CreateTopicRequest.ConfigEntry(k, v)
                    }.ifEmpty { null }
                )

                dataPlaneCache.createTopic(request)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            thisLogger().warn("Failed to create topic '$name'", e)
            Result.failure(e)
        } finally {
            // Update UI on main thread (EDT) - refresh regardless of success/failure
            invokeLater {
                confluentDataManager.updater.invokeRefreshModel(topicModel)
            }
        }
    }

    override suspend fun deleteTopic(topicNames: List<String>): Result<Unit> {
        return try {
            if (topicNames.isEmpty()) {
                return Result.success(Unit)
            }

            withContext(Dispatchers.IO) {
                topicNames.forEach { topicName ->
                    dataPlaneCache.deleteTopic(topicName)
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            thisLogger().warn("Failed to delete topics: $topicNames", e)
            Result.failure(e)
        } finally {
            // Update UI on main thread (EDT) - refresh regardless of success/failure
            invokeLater {
                confluentDataManager.updater.invokeRefreshModel(topicModel)
            }
        }
    }

    override suspend fun clearTopic(topicName: String): Result<Unit> {
        return Result.failure(UnsupportedOperationException("Clear topic not supported for Confluent Cloud"))
    }

    override fun clearPartitions(partitions: List<BdtTopicPartition>) {
        // CCloud REST API doesn't support clearing partitions
    }

    override fun supportsClearPartitions(): Boolean = false

    private fun createTopicPartitionsStorage() = ObjectDataModelStorage<String, BdtTopicPartition>(
        confluentDataManager.updater,
        BdtTopicPartition::partitionId,
        dependOn = topicModel
    ) { topicName ->
        try {
            runBlocking(Dispatchers.IO) {
                dataPlaneCache.getTopicPartitions(topicName)
            }
        } catch (e: Exception) {
            thisLogger().warn("Failed to load partitions for $topicName", e)
            emptyList()
        }
    }

    private fun createTopicConfigsStorage() = ObjectDataModelStorage<String, TopicConfig>(
        confluentDataManager.updater,
        TopicConfig::name
    ) { topicName ->
        try {
            val showFullConfig = KafkaToolWindowSettings.getInstance().showFullTopicConfig
            runBlocking(Dispatchers.IO) {
                dataPlaneCache.getTopicConfigs(topicName, showFullConfig)
            }
        } catch (e: Exception) {
            thisLogger().warn("Failed to load configs for $topicName", e)
            emptyList()
        }
    }

    /**
     * CCloud REST API does not expose replicas endpoint, so ISR data is not available.
     */
    override fun supportsInSyncReplicasData(): Boolean = false

    override fun dispose() {
    }
}
