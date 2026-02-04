package io.confluent.intellijplugin.data

import com.intellij.CommonBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import io.confluent.intellijplugin.client.BdtKafkaMapper
import io.confluent.intellijplugin.client.KafkaClient
import io.confluent.intellijplugin.common.models.RegistrySchemaInEditor
import io.confluent.intellijplugin.consumer.editor.KafkaConsumerPanelStorage
import io.confluent.intellijplugin.core.connection.updater.IntervalUpdateSettings
import io.confluent.intellijplugin.core.monitoring.data.storage.RootDataModelStorage
import io.confluent.intellijplugin.core.monitoring.rfs.MonitoringDriver
import io.confluent.intellijplugin.core.rfs.driver.SafeExecutor
import io.confluent.intellijplugin.core.rfs.driver.manager.DriverManager
import io.confluent.intellijplugin.core.rfs.util.RfsNotificationUtils
import io.confluent.intellijplugin.core.util.asSilent
import io.confluent.intellijplugin.core.util.runAsync
import io.confluent.intellijplugin.core.util.runAsyncSuspend
import io.confluent.intellijplugin.model.*
import io.confluent.intellijplugin.registry.KafkaRegistryFormat
import io.confluent.intellijplugin.registry.SchemaVersionInfo
import io.confluent.intellijplugin.registry.common.KafkaSchemaInfo
import io.confluent.intellijplugin.rfs.KafkaConnectionData
import io.confluent.intellijplugin.rfs.KafkaDriver
import io.confluent.intellijplugin.toolwindow.config.KafkaToolWindowSettings
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import io.confluent.kafka.schemaregistry.ParsedSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.asPromise
import org.jetbrains.concurrency.await
import software.amazon.awssdk.services.glue.model.Compatibility
import java.util.concurrent.ConcurrentSkipListMap
import kotlin.time.Duration

/**
 * Data manager for Kafka connections using AdminClient protocol.
 * Handles topics, consumer groups, and schema registry operations.
 */
class KafkaDataManager(
    project: Project?,
    override val connectionData: KafkaConnectionData,
    settings: IntervalUpdateSettings,
    driverProvider: () -> MonitoringDriver
) : BaseClusterDataManager(project, settings, driverProvider) {
    override val registryType = connectionData.registryType
    override val connectionId = connectionData.innerId
    override val client = KafkaClient(project, connectionData, false).also { Disposer.register(this, it) }
    val consumerPanelStorage = KafkaConsumerPanelStorage(this).also { Disposer.register(this, it) }

    private val cacheSchemaType = ConcurrentSkipListMap<String, KafkaRegistryFormat>()

    init {
        init()
        RootDataModelStorage(
            updater, listOfNotNull(
                topicModel,
                schemaRegistryModel,
                consumerGroupsModel
            )
        ).also { Disposer.register(this, it) }
    }

    override suspend fun loadTopics(): List<TopicPresentable> = withContext(Dispatchers.IO) {
        try {
            val toolWindowSettings = KafkaToolWindowSettings.getInstance()
            client.getTopics(toolWindowSettings.showInternalTopics)
        } catch (t: Throwable) {
            thisLogger().warn("Failed to load topics", t)
            emptyList()
        }
    }

    override suspend fun loadDetailedTopicsInfo(
        topics: List<TopicPresentable>
    ): Pair<List<TopicPresentable>, Throwable?> = withContext(Dispatchers.IO) {
        try {
            val detailedTopics = client.getDetailedTopicsInfo(topics.map { it.name })
            detailedTopics to null
        } catch (t: Throwable) {
            topics.map { BdtKafkaMapper.mockInternalTopic(it.name) } to t
        }
    }

    override suspend fun fetchTopicPartitions(topicName: String): List<BdtTopicPartition> {
        val topics = topicModel.data ?: emptyList()
        val topic = topics.find { it.name == topicName }
            ?: error(KafkaMessagesBundle.message("topic.not.found", topicName))
        return topic.partitionList
    }

    override suspend fun getTopicConfig(
        topicName: String,
        showFullConfig: Boolean
    ): List<TopicConfig> = withContext(Dispatchers.IO) {
        val configs = client.getTopicConfig(topicName)
        if (showFullConfig) configs else configs.filter { it.value != it.defaultValue }
    }

    override suspend fun loadConsumerGroups(): List<ConsumerGroupPresentable> = withContext(Dispatchers.IO) {
        try {
            client.getConsumerGroups()
        } catch (t: Throwable) {
            thisLogger().warn("Failed to load consumer groups", t)
            emptyList()
        }
    }

    override suspend fun listConsumerGroupOffsets(consumerGroup: String): List<ConsumerGroupOffsetInfo> {
        val listConsumerGroupOffsets = client.listConsumerGroupOffsets(consumerGroup)
        val cachedOffsets = topicModel.data
            ?.flatMap { it.partitionList }
            ?.mapNotNull { partition ->
                partition.endOffset?.let { offset ->
                    TopicPartition(partition.topic, partition.partitionId) to offset
                }
            }
            ?.toMap()
            ?: emptyMap()
        val notLoadedOffsets = listConsumerGroupOffsets.keys - cachedOffsets.keys
        val loaded = client.loadLatestOffsets(notLoadedOffsets)
        val allOffsets = cachedOffsets + loaded
        return listConsumerGroupOffsets.map {
            val topic = it.key.topic()
            val partition = it.key.partition()
            val offset = it.value.offset()
            val topicEndOffset = allOffsets[it.key]?.minus(offset)
            ConsumerGroupOffsetInfo(
                topic = topic,
                partition = partition,
                offset = offset,
                lag = topicEndOffset
            )
        }.distinct()
    }

    override suspend fun listSchemasNames(limit: Int?, filter: String?): Pair<List<KafkaSchemaInfo>, Boolean> {
        return client.confluentRegistryClient?.listSchemas(limit, filter, false, connectionId)
            ?: client.glueRegistryClient?.listSchemas(limit, filter, connectionId)
            ?: (emptyList<KafkaSchemaInfo>() to false)
    }

    override suspend fun updateSchemaList(
        schemas: List<KafkaSchemaInfo>
    ): Pair<List<KafkaSchemaInfo>, Throwable?> = withContext(Dispatchers.IO) {
        try {
            cacheSchemaType.clear()
            val loadedInfo = schemas.map {
                runAsyncSuspend {
                    try {
                        loadSchema(it.name)
                    } catch (t: Throwable) {
                        thisLogger().warn(t)
                        it
                    }
                }
            }.map { it.await() }
            loadedInfo to null
        } catch (t: Throwable) {
            schemas.map { it.copy(type = null, version = null) } to t
        }
    }

    override suspend fun listSchemaVersions(schemaName: String): List<Long> {
        return client.confluentRegistryClient?.listSchemaVersions(schemaName)
            ?: client.glueRegistryClient?.listSchemaVersions(schemaName)
            ?: emptyList()
    }

    @RequiresBackgroundThread
    fun loadTopicNames() = try {
        client.getTopics(false)
    } catch (t: Throwable) {
        thisLogger().warn(t)
        emptyList()
    }

    fun getCachedTopicByName(name: String) = getTopics().firstOrNull { it.name == name }

    suspend fun loadConsumerGroupOffset(name: String): List<ConsumerGroupOffsetInfo> {
        return listConsumerGroupOffsets(name)
    }

    @RequiresBackgroundThread
    suspend fun loadTopicInfo(name: String) = client.getDetailedTopicsInfo(listOf(name)).first()

    fun initRefreshSchemasIfRequired() {
        val schemaModel = schemaRegistryModel
        if (schemaModel?.isInitedByFirstTime == false) {
            updater.invokeRefreshModel(schemaModel)
        }
    }

    @RequiresBackgroundThread
    fun getSchemasForEditor() = try {
        val (schemas, _) = client.confluentRegistryClient?.listSchemas(null, null, false, connectionId)
            ?: client.glueRegistryClient?.listSchemas(null, null, connectionId)
            ?: (emptyList<KafkaSchemaInfo>() to false)
        schemas.map {
            RegistrySchemaInEditor(schemaName = it.name, schemaFormat = getCachedOrLoadSchemaType(it.name))
        }.sorted()
    } catch (t: Throwable) {
        thisLogger().warn(t)
        emptyList()
    }

    fun getSchemaVersionsModel(schemaName: String) = schemaVersionModels[schemaName]

    fun getSchemaVersionInfo(schemaName: String, version: Long): Promise<SchemaVersionInfo> = runAsync {
        client.glueRegistryClient?.getSchemaVersionInfo(schemaName, version)
            ?: client.confluentRegistryClient?.getSchemaVersionInfo(schemaName, version)
            ?: error("Schema Registry provider is not selected")
    }

    fun deleteRegistrySchemaVersion(versionSchema: SchemaVersionInfo) {
        driver.coroutineScope.launch {
            try {
                client.confluentRegistryClient?.deleteSchemaVersion(versionSchema)
                    ?: client.glueRegistryClient?.deleteSchemaVersion(versionSchema)
                updater.invokeRefreshModel(schemaVersionModels[versionSchema.schemaName])
                schemaRegistryModel?.let { updater.invokeRefreshModel(it) }
            } catch (t: Throwable) {
                RfsNotificationUtils.showExceptionMessage(project, t)
            }
        }
    }

    fun updateSchema(versionInfo: SchemaVersionInfo, newText: String) = SafeExecutor.instance.asyncSuspend(
        taskName = null,
        timeout = Duration.INFINITE
    ) {
        runInterruptible(Dispatchers.IO) {
            client.confluentRegistryClient?.updateSchema(versionInfo, newText)
                ?: client.glueRegistryClient?.updateSchema(versionInfo, newText)
                ?: error("Schema registry not configured")
            schemaRegistryModel?.let { updater.invokeRefreshModel(it) }
        }
        updater.invokeRefreshModel(schemaVersionModels[versionInfo.schemaName])
    }.deferred.asCompletableFuture().asPromise().asSilent()

    fun deleteSchema(schemaName: String) {
        driver.coroutineScope.launch {
            try {
                val registryInfo = getCachedSchema(schemaName) ?: return@launch
                val confirmed = if (registryInfo.isSoftDeleted) {
                    withContext(Dispatchers.EDT) {
                        Messages.showOkCancelDialog(
                            project,
                            KafkaMessagesBundle.message("action.remove.schema.confirm.dialog.msg.permanent", registryInfo.name),
                            KafkaMessagesBundle.message("action.remove.schema.confirm.dialog.title"),
                            Messages.getOkButton(),
                            Messages.getCancelButton(),
                            Messages.getQuestionIcon()
                        ) == Messages.OK
                    }
                } else {
                    withContext(Dispatchers.EDT) {
                        Messages.showOkCancelDialog(
                            project,
                            KafkaMessagesBundle.message("action.remove.schema.confirm.dialog.msg.soft", registryInfo.name),
                            KafkaMessagesBundle.message("action.remove.schema.confirm.dialog.title"),
                            Messages.getOkButton(),
                            Messages.getCancelButton(),
                            Messages.getQuestionIcon()
                        ) == Messages.OK
                    }
                }
                if (!confirmed) return@launch
                deleteSchemaWithoutConfirmation(registryInfo.name, permanent = registryInfo.isSoftDeleted)
            } catch (t: Throwable) {
                RfsNotificationUtils.showExceptionMessage(project, t)
            }
        }
    }

    private suspend fun deleteSchemaWithoutConfirmation(schemaName: String, permanent: Boolean) =
        withContext(Dispatchers.IO) {
            client.confluentRegistryClient?.deleteSchema(schemaName, permanent)
                ?: client.glueRegistryClient?.deleteSchema(schemaName)
            schemaRegistryModel?.let { updater.invokeRefreshModel(it) }
        }

    fun createSchema(schemaName: String, parsedSchema: ParsedSchema) = runAsync {
        client.confluentRegistryClient?.createSchema(schemaName, parsedSchema)
            ?: client.glueRegistryClient?.createSchema(
                schemaName,
                parsedSchema.schemaType(),
                parsedSchema.canonicalString(),
                Compatibility.BACKWARD,
                "",
                emptyMap()
            )
        schemaRegistryModel?.let { updater.invokeRefreshModel(it) }
    }

    fun isSchemaExists(name: String) = getCachedSchema(name) != null

    fun getCachedOrLoadSchema(name: String): KafkaSchemaInfo =
        getCachedSchema(name)?.takeIf { it.type != null } ?: loadSchema(name)

    private fun getCachedOrLoadSchemaType(name: String) =
        cacheSchemaType[name] ?: getCachedOrLoadSchema(name).type?.also {
            cacheSchemaType[name] = it
        }

    private fun loadSchema(schemaName: String) =
        client.confluentRegistryClient?.loadSchemaInfo(schemaName)
            ?: client.glueRegistryClient?.loadSchemaInfo(schemaName)
            ?: error("Schema registry not configured")

    fun getLatestVersionInfo(schemaName: String) =
        client.confluentRegistryClient?.getLatestVersionInfo(schemaName)
            ?: client.glueRegistryClient?.getLatestVersionInfo(schemaName)

    fun createTopic(name: String, numPartition: Int?, replicaFactor: Int?) = actionWrapper {
        client.createTopic(name, numPartition, replicaFactor)
        updater.invokeRefreshModel(topicModel)
    }

    private fun actionWrapper(body: () -> Unit) = driver.coroutineScope.launch {
        try {
            body()
        } catch (t: Throwable) {
            RfsNotificationUtils.showExceptionMessage(project, t)
        }
    }

    fun clearTopicWithConfirmation(topicName: String) {
        driver.coroutineScope.launch {
            try {
                val topic = getCachedTopicInfo(topicName) ?: return@launch
                val msg = KafkaMessagesBundle.message("action.clear.topic.single.message", topic.name)
                val res = withContext(Dispatchers.EDT) {
                    Messages.showOkCancelDialog(
                        project,
                        msg,
                        KafkaMessagesBundle.message("action.kafka.ClearTopicAction.text"),
                        CommonBundle.getOkButtonText(),
                        CommonBundle.getCancelButtonText(),
                        Messages.getQuestionIcon()
                    )
                }
                if (res != Messages.OK) return@launch
                clearPartitionsInternal(topic.partitionList)
            } catch (t: Throwable) {
                RfsNotificationUtils.showExceptionMessage(project, t)
            }
        }
    }

    private fun getCachedTopicInfo(topicName: String) =
        topicModel.data?.firstOrNull { it.name == topicName }

    override fun clearPartitions(partitions: List<BdtTopicPartition>) {
        if (partitions.isEmpty()) return

        driver.coroutineScope.launch {
            try {
                val msg = if (partitions.size == 1)
                    KafkaMessagesBundle.message(
                        "action.clear.partition.single.message",
                        partitions.first().let { "${it.topic}-${it.partitionId}" })
                else
                    KafkaMessagesBundle.message("action.clear.partition.multi.message", partitions.size)

                val res = withContext(Dispatchers.EDT) {
                    Messages.showOkCancelDialog(
                        project,
                        msg,
                        KafkaMessagesBundle.message("action.clear.partition.title"),
                        CommonBundle.getOkButtonText(),
                        CommonBundle.getCancelButtonText(),
                        Messages.getQuestionIcon()
                    )
                }
                if (res != Messages.OK) return@launch
                clearPartitionsInternal(partitions)
            } catch (t: Throwable) {
                RfsNotificationUtils.showExceptionMessage(project, t)
            }
        }
    }

    private fun clearPartitionsInternal(partitions: List<BdtTopicPartition>) {
        runAsyncSuspend {
            try {
                client.clearPartitions(partitions)
            } catch (t: Throwable) {
                RfsNotificationUtils.showExceptionMessage(project, t)
            } finally {
                updater.invokeRefreshModel(topicModel)
            }
        }
    }

    suspend fun resetOffsets(consumeGroupId: String, offsets: Map<TopicPartition, OffsetAndMetadata>) {
        client.resetOffsets(consumeGroupId, offsets)
        updater.invokeRefreshModel(consumerGroupsModel)
        updater.invokeRefreshModel(consumerGroupsOffsets[consumeGroupId])
    }

    suspend fun getOffsetsForData(partitions: Set<TopicPartition>, timestamp: Long): Map<TopicPartition, Long> =
        client.getOffsetsForDate(partitions.toList(), timestamp)

    fun deleteConsumerGroup(name: String) {
        driver.coroutineScope.launch {
            try {
                val msg = KafkaMessagesBundle.message("action.delete.consumer.group.single.message", name)
                val res = withContext(Dispatchers.EDT) {
                    Messages.showOkCancelDialog(
                        project,
                        msg,
                        KafkaMessagesBundle.message("action.kafka.DeleteConsumerGroupAction.text"),
                        CommonBundle.getOkButtonText(),
                        CommonBundle.getCancelButtonText(),
                        Messages.getQuestionIcon()
                    )
                }
                if (res != Messages.OK) return@launch

                client.deleteConsumerGroup(name)
                updater.invokeRefreshModel(consumerGroupsModel)
            } catch (t: Throwable) {
                RfsNotificationUtils.showExceptionMessage(project, t)
            }
        }
    }

    override suspend fun createTopic(
        name: String,
        partitions: Int?,
        replicationFactor: Int?,
        configs: Map<String, String>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            client.createTopic(name, partitions, replicationFactor)
            updater.invokeRefreshModel(topicModel)
            Result.success(Unit)
        } catch (e: Exception) {
            thisLogger().warn("Failed to create topic '$name'", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteTopic(topicNames: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (topicNames.isEmpty()) return@withContext Result.success(Unit)
            topicNames.forEach { client.deleteTopic(it) }
            updater.invokeRefreshModel(topicModel)
            Result.success(Unit)
        } catch (e: Exception) {
            thisLogger().warn("Failed to delete topics: $topicNames", e)
            Result.failure(e)
        }
    }

    override suspend fun clearTopic(topicName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val topic = getCachedTopicInfo(topicName)
                ?: return@withContext Result.failure(IllegalArgumentException("Topic not found: $topicName"))
            clearPartitionsInternal(topic.partitionList)
            Result.success(Unit)
        } catch (e: Exception) {
            thisLogger().warn("Failed to clear topic '$topicName'", e)
            Result.failure(e)
        }
    }

    companion object {
        fun getInstance(connectionId: String, project: Project) =
            (DriverManager.getDriverById(project, connectionId) as? KafkaDriver)?.dataManager

        fun List<KafkaSchemaInfo>.sortedSchemas(connectionId: String): List<KafkaSchemaInfo> {
            val kafkaSettings = KafkaToolWindowSettings.getInstance()
            val config = kafkaSettings.getOrCreateConfig(connectionId)
            val schemas = this.map { schema -> schema.copy(isFavorite = config.schemasPined.contains(schema.name)) }
            val finalSchemas = if (kafkaSettings.showFavoriteSchema) schemas.filter { it.isFavorite } else schemas
            return finalSchemas.sortedWith(compareByDescending<KafkaSchemaInfo> { it.isFavorite }.thenBy { it.name.lowercase() })
        }
    }
}
