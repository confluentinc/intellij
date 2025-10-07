package io.confluent.intellijplugin.data

import com.intellij.CommonBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import io.confluent.intellijplugin.client.BdtKafkaMapper
import io.confluent.intellijplugin.client.KafkaClient
import io.confluent.intellijplugin.common.models.RegistrySchemaInEditor
import io.confluent.intellijplugin.consumer.editor.KafkaConsumerPanelStorage
import io.confluent.intellijplugin.core.connection.updater.IntervalUpdateSettings
import io.confluent.intellijplugin.core.monitoring.data.MonitoringDataManager
import io.confluent.intellijplugin.core.monitoring.data.model.FieldGroupsData
import io.confluent.intellijplugin.core.monitoring.data.model.ObjectDataModel
import io.confluent.intellijplugin.core.monitoring.data.storage.FieldGroupsDataModelStorage
import io.confluent.intellijplugin.core.monitoring.data.storage.ObjectDataModelStorage
import io.confluent.intellijplugin.core.monitoring.data.storage.RootDataModelStorage
import io.confluent.intellijplugin.core.rfs.driver.SafeExecutor
import io.confluent.intellijplugin.core.rfs.driver.manager.DriverManager
import io.confluent.intellijplugin.core.rfs.util.RfsNotificationUtils
import io.confluent.intellijplugin.core.util.asSilent
import io.confluent.intellijplugin.core.util.runAsync
import io.confluent.intellijplugin.core.util.runAsyncSuspend
import io.confluent.intellijplugin.model.*
import io.confluent.intellijplugin.registry.KafkaRegistryFormat
import io.confluent.intellijplugin.registry.KafkaRegistryType
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

class KafkaDataManager(
    project: Project?,
    override val connectionData: KafkaConnectionData,
    settings: IntervalUpdateSettings
) : MonitoringDataManager(project, settings) {
    val registryType = connectionData.registryType

    val connectionId = connectionData.innerId
    override val client = KafkaClient(project, connectionData, false).also { Disposer.register(this, it) }
    val consumerPanelStorage = KafkaConsumerPanelStorage(this).also { Disposer.register(this, it) }

    private val cacheSchemaType = ConcurrentSkipListMap<String, KafkaRegistryFormat>()
    internal val topicModel = createTopicsDataModel().also { Disposer.register(this, it) }

    internal val consumerGroupsModel = createConsumerGroupsDataModel().also { Disposer.register(this, it) }
    internal val consumerGroupsOffsets = createConsumerGroupOffsetsStorage().also { Disposer.register(this, it) }
    var topicPartitionsModels = createTopicPartitionsStorage().also { Disposer.register(this, it) }
    val topicConfigsModels = createTopicConfigsStorage().also { Disposer.register(this, it) }

    private val schemaVersionModels = createSchemaVersionsStorage().also { Disposer.register(this, it) }


    internal val schemaRegistryModel = createSchemaRegistryDataModel()?.also {
        Disposer.register(this, it)
    }

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

    fun getTopics() = topicModel.data ?: emptyList()

    @RequiresBackgroundThread
    fun loadTopicNames() = try {
        client.getTopics(false)
    } catch (t: Throwable) {
        thisLogger().warn(t)
        emptyList()
    }

    fun getCachedTopicByName(name: String) = getTopics().firstOrNull { it.name == name }

    @RequiresBackgroundThread
    suspend fun loadTopicInfo(name: String) = client.getDetailedTopicsInfo(listOf(name)).first()

    @RequiresBackgroundThread
    fun loadConsumerGroupOffset(name: String): List<ConsumerGroupOffsetInfo> {
        return runBlockingMaybeCancellable {
            val listConsumerGroupOffsets = client.listConsumerGroupOffsets(name)
            val cachedOffsets = topicModel.data
                ?.flatMap { it.partitionList }
                ?.associate { TopicPartition(it.topic, it.partitionId) to it.endOffset }
                ?: emptyMap()
            val notLoadedOffsets = listConsumerGroupOffsets.keys - cachedOffsets.keys
            val loaded = client.loadLatestOffsets(notLoadedOffsets)
            val allOffsets = cachedOffsets + loaded
            val topicsToPartitions = listConsumerGroupOffsets.map {
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
            topicsToPartitions
        }
    }


    fun getSchemaByName(name: String) = schemaRegistryModel?.data?.firstOrNull { it.name == name }

    fun createTopic(name: String, numPartition: Int?, replicaFactor: Int?) = actionWrapper {
        client.createTopic(name, numPartition, replicaFactor)
        updater.invokeRefreshModel(topicModel)
    }

    fun initRefreshSchemasIfRequired() {
        val schemaModel = schemaRegistryModel
        if (schemaModel?.isInitedByFirstTime == false) {
            updater.invokeRefreshModel(schemaModel)
        }
    }

    @RequiresBackgroundThread
    fun getSchemasForEditor() = try {
        listSchemasNames(null, null).first.map {
            RegistrySchemaInEditor(schemaName = it.name, schemaFormat = getCachedOrLoadSchemaType(it.name))
        }.sorted()
    } catch (t: Throwable) {
        thisLogger().warn(t)
        emptyList()
    }

    fun deleteTopic(topicNames: List<String>) {
        if (topicNames.isEmpty()) {
            return
        }

        val msg = if (topicNames.size == 1)
            KafkaMessagesBundle.message("action.delete.topic.single.message", topicNames.first())
        else
            KafkaMessagesBundle.message("action.delete.topic.multi.message", topicNames.size)

        val res = Messages.showOkCancelDialog(
            project,
            msg,
            KafkaMessagesBundle.message("action.delete.topic.title"),
            CommonBundle.getOkButtonText(),
            CommonBundle.getCancelButtonText(),
            Messages.getQuestionIcon()
        )
        if (res != Messages.OK)
            return

        actionWrapper {
            topicNames.forEach {
                client.deleteTopic(it)
            }

            updater.invokeRefreshModel(topicModel)
        }
    }

    private fun createTopicsDataModel() = ObjectDataModel(TopicPresentable::name, additionalInfoLoading = { model ->
        try {
            client.getDetailedTopicsInfo(model.data?.map { it.name } ?: emptyList()) to null
        } catch (t: Throwable) {
            (model.data?.map { BdtKafkaMapper.mockInternalTopic(it.name) } ?: emptyList()) to t
        }
    }) {
        val toolWindowSettings = KafkaToolWindowSettings.getInstance()
        val topicFilterName = toolWindowSettings.getOrCreateConfig(connectionId).topicFilterName
        val topics = client.getTopics(toolWindowSettings.showInternalTopics).filter {
            topicFilterName == null || it.name.lowercase().contains(topicFilterName.lowercase())
        }

        val topicLimit = KafkaToolWindowSettings.getInstance().getOrCreateConfig(connectionId).topicLimit
        (topicLimit?.let { topics.take(it) } ?: topics) to (topicLimit != null && topics.size > topicLimit)
    }

    private fun createConsumerGroupsDataModel() =
        ObjectDataModel(ConsumerGroupPresentable::consumerGroup) {
            val toolWindowSettings = KafkaToolWindowSettings.getInstance()
            val filterName = toolWindowSettings.getOrCreateConfig(connectionId).consumerFilterName

            val groups = client.getConsumerGroups().filter {
                filterName == null || it.consumerGroup.contains(filterName)
            }
            val limit = KafkaToolWindowSettings.getInstance().getOrCreateConfig(connectionId).consumerLimit
            (limit?.let { groups.take(it) } ?: groups) to (limit != null && groups.size > limit)
        }

    private fun actionWrapper(body: () -> Unit) = driver.coroutineScope.launch {
        try {
            body()
        } catch (t: Throwable) {
            RfsNotificationUtils.showExceptionMessage(project, t)
        }
    }


    private fun createConsumerGroupOffsetsStorage() = ObjectDataModelStorage<String, ConsumerGroupOffsetInfo>(
        updater,
        ConsumerGroupOffsetInfo::fullId
    ) { consumerGroup ->
        loadConsumerGroupOffset(consumerGroup)
    }


    private fun createTopicPartitionsStorage() = ObjectDataModelStorage<String, BdtTopicPartition>(
        updater,
        BdtTopicPartition::partitionId,
        dependOn = topicModel
    ) { topicName ->
        val topics = topicModel.data ?: emptyList()
        val topic =
            topics.find { it.name == topicName } ?: error(KafkaMessagesBundle.message("topic.not.found", topicName))
        topic.partitionList
    }

    private fun createTopicConfigsStorage() = ObjectDataModelStorage<String, TopicConfig>(
        updater,
        TopicConfig::name
    ) { topicName ->
        val configs = client.getTopicConfig(topicName)

        if (KafkaToolWindowSettings.getInstance().showFullTopicConfig)
            configs
        else
            configs.filter { it.value != it.defaultValue }
    }


    private fun createSchemaVersionsStorage() = FieldGroupsDataModelStorage<String, List<Long>>(updater) { schemaName ->
        val versions = client.confluentRegistryClient?.listSchemaVersions(schemaName)
            ?: client.glueRegistryClient?.listSchemaVersions(schemaName)
            ?: emptyList()
        FieldGroupsData(versions.sortedDescending(), emptyList())
    }

    fun getSchemaVersionsModel(schemaName: String) = schemaVersionModels[schemaName]

    fun getSchemaVersionInfo(schemaName: String, version: Long): Promise<SchemaVersionInfo> = runAsync {
        client.glueRegistryClient?.getSchemaVersionInfo(schemaName, version)
            ?: client.confluentRegistryClient?.getSchemaVersionInfo(schemaName, version)
            ?: error("Schema Registry provider is not selected")
    }

    fun deleteRegistrySchemaVersion(versionSchema: SchemaVersionInfo) = actionWrapper {
        client.confluentRegistryClient?.deleteSchemaVersion(versionSchema)
            ?: client.glueRegistryClient?.deleteSchemaVersion(versionSchema)
        updater.invokeRefreshModel(schemaVersionModels[versionSchema.schemaName])
        schemaRegistryModel?.let { updater.invokeRefreshModel(it) }

    }

    fun updateSchema(versionInfo: SchemaVersionInfo, newText: String) = SafeExecutor.instance.asyncSuspend(
        taskName = null,
        timeout = Duration.INFINITE
    ) {
        runInterruptible(Dispatchers.IO) {
            client.confluentRegistryClient?.updateSchema(versionInfo, newText)
                ?: client.glueRegistryClient?.updateSchema(versionInfo, newText)
                ?: error("Not Fond registry")
            schemaRegistryModel?.let { updater.invokeRefreshModel(it) }
        }
        updater.invokeRefreshModel(schemaVersionModels[versionInfo.schemaName])
    }.deferred.asCompletableFuture().asPromise().asSilent()

    fun deleteSchema(schemaName: String) = actionWrapperSuspend {
        val registryInfo = getCachedSchema(schemaName) ?: return@actionWrapperSuspend
        if (registryInfo.isSoftDeleted) {
            val confirm = withContext(Dispatchers.IO) {
                Messages.showOkCancelDialog(
                    project,
                    KafkaMessagesBundle.message(
                        "action.remove.schema.confirm.dialog.msg.permanent",
                        registryInfo.name
                    ),
                    KafkaMessagesBundle.message("action.remove.schema.confirm.dialog.title"),
                    Messages.getOkButton(),
                    Messages.getCancelButton(),
                    Messages.getQuestionIcon()
                )
            }

            if (confirm != Messages.OK)
                return@actionWrapperSuspend
            deleteSchemaWithoutConfirmation(registryInfo.name, permanent = true)
        } else {
            val confirm = withContext(Dispatchers.EDT) {
                Messages.showOkCancelDialog(
                    project,
                    KafkaMessagesBundle.message("action.remove.schema.confirm.dialog.msg.soft", registryInfo.name),
                    KafkaMessagesBundle.message("action.remove.schema.confirm.dialog.title"),
                    Messages.getOkButton(),
                    Messages.getCancelButton(),
                    Messages.getQuestionIcon()
                ) == Messages.OK
            }
            if (!confirm)
                return@actionWrapperSuspend
            deleteSchemaWithoutConfirmation(registryInfo.name, permanent = false)
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
                parsedSchema.canonicalString(), Compatibility.BACKWARD, "",
                emptyMap()
            )
        schemaRegistryModel?.let { updater.invokeRefreshModel(it) }
    }

    private fun createSchemaRegistryDataModel(): ObjectDataModel<KafkaSchemaInfo>? {
        if (registryType == KafkaRegistryType.NONE)
            return null
        val dataModel = ObjectDataModel(KafkaSchemaInfo::name, additionalInfoLoading = { dataModel ->
            try {
                cacheSchemaType.clear()
                updateSchemaList(dataModel) to null
            } catch (t: Throwable) {
                (dataModel.data?.map { BdtKafkaMapper.mockKafkaSchemaInfo(it) } ?: emptyList()) to t
            }
        }) {
            val filter = KafkaToolWindowSettings.getInstance().getOrCreateConfig(connectionId).schemaFilterName
            val limit = KafkaToolWindowSettings.getInstance().getOrCreateConfig(connectionId).registryLimit
            listSchemasNames(limit, filter)

        }
        return dataModel
    }

    private fun listSchemasNames(limit: Int?, filter: String?, registryShowDeletedSubjects: Boolean = false) =
        client.confluentRegistryClient?.listSchemas(limit, filter, registryShowDeletedSubjects, connectionId)
            ?: client.glueRegistryClient?.listSchemas(limit, filter, connectionId)
            ?: (emptyList<KafkaSchemaInfo>() to false)

    private suspend fun updateSchemaList(dataModel: ObjectDataModel<KafkaSchemaInfo>): List<KafkaSchemaInfo> {
        val data = dataModel.data ?: emptyList()

        val loadedInfo = data.map {
            runAsyncSuspend {
                try {
                    loadSchema(it.name)
                } catch (t: Throwable) {
                    thisLogger().warn(t)
                    it
                }
            }
        }.map { it.await() }
        return loadedInfo.sortedSchemas(connectionId)
    }

    private fun loadSchema(schemaName: String) =
        client.confluentRegistryClient?.loadSchemaInfo(schemaName) ?: client.glueRegistryClient?.loadSchemaInfo(
            schemaName
        ) ?: error("Not used")

    fun isSchemaExists(name: String) = getCachedSchema(name) != null

    fun getCachedOrLoadSchema(name: String): KafkaSchemaInfo =
        getCachedSchema(name)?.takeIf { it.type != null } ?: loadSchema(name)

    private fun getCachedOrLoadSchemaType(name: String) =
        cacheSchemaType[name] ?: getCachedOrLoadSchema(name).type?.also {
            cacheSchemaType[name] = it
        }

    fun getCachedSchema(name: String) = schemaRegistryModel?.data?.firstOrNull { it.name == name }

    fun getLatestVersionInfo(schemaName: String) =
        client.confluentRegistryClient?.getLatestVersionInfo(schemaName)
            ?: client.glueRegistryClient?.getLatestVersionInfo(schemaName)

    fun clearTopic(topicName: String) {
        val topic = getCachedTopicInfo(topicName) ?: return
        val msg = KafkaMessagesBundle.message("action.clear.topic.single.message", topic.name)
        val res = Messages.showOkCancelDialog(
            project,
            msg,
            KafkaMessagesBundle.message("action.kafka.ClearTopicAction.text"),
            CommonBundle.getOkButtonText(),
            CommonBundle.getCancelButtonText(),
            Messages.getQuestionIcon()
        )
        if (res != Messages.OK)
            return

        clearPartitionsInternal(topic.partitionList)
    }

    private fun getCachedTopicInfo(topicName: String) =
        topicModel.data?.firstOrNull { it.name == topicName }


    fun clearPartitions(partitions: List<BdtTopicPartition>) {
        if (partitions.isEmpty()) {
            return
        }

        val msg = if (partitions.size == 1)
            KafkaMessagesBundle.message(
                "action.clear.partition.single.message",
                partitions.first().let { "${it.topic}-${it.partitionId}" })
        else
            KafkaMessagesBundle.message("action.clear.partition.multi.message", partitions.size)

        val res = Messages.showOkCancelDialog(
            project,
            msg,
            KafkaMessagesBundle.message("action.clear.partition.title"),
            CommonBundle.getOkButtonText(),
            CommonBundle.getCancelButtonText(),
            Messages.getQuestionIcon()
        )
        if (res != Messages.OK)
            return

        clearPartitionsInternal(partitions)
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

    fun updatePinedTopics(topicName: String, isForAdding: Boolean) {
        val config = KafkaToolWindowSettings.getInstance().getOrCreateConfig(connectionId)
        if (isForAdding) {
            config.topicsPined += topicName
        } else {
            config.topicsPined -= topicName
        }
        updater.invokeRefreshModel(topicModel)
    }

    fun updatePinedConsumerGroups(consumerGorup: String, isForAdding: Boolean) {
        val config = KafkaToolWindowSettings.getInstance().getOrCreateConfig(connectionId)
        if (isForAdding) {
            config.consumerGroupPined += consumerGorup
        } else {
            config.consumerGroupPined -= consumerGorup
        }
        updater.invokeRefreshModel(consumerGroupsModel)
    }

    fun updatePinedSchemas(schemaName: String, isForAdding: Boolean) {
        val config = KafkaToolWindowSettings.getInstance().getOrCreateConfig(connectionId)
        if (isForAdding) {
            config.schemasPined += schemaName
        } else {
            config.schemasPined -= schemaName
        }
        schemaRegistryModel?.let { updater.invokeRefreshModel(it) }
    }

    suspend fun resetOffsets(consumeGroupId: String, offsets: Map<TopicPartition, OffsetAndMetadata>) {
        client.resetOffsets(consumeGroupId, offsets)
        updater.invokeRefreshModel(consumerGroupsModel)
        updater.invokeRefreshModel(consumerGroupsOffsets[consumeGroupId])
    }

    suspend fun getOffsetsForData(partitions: Set<TopicPartition>, timestamp: Long): Map<TopicPartition, Long> {
        return client.getOffsetsForDate(partitions.toList(), timestamp)
    }

    fun deleteConsumerGroup(name: String) {
        val msg = KafkaMessagesBundle.message("action.delete.consumer.group.single.message", name)
        val res = Messages.showOkCancelDialog(
            project,
            msg,
            KafkaMessagesBundle.message("action.kafka.DeleteConsumerGroupAction.text"),
            CommonBundle.getOkButtonText(),
            CommonBundle.getCancelButtonText(),
            Messages.getQuestionIcon()
        )
        if (res != Messages.OK)
            return

        actionWrapper {
            client.deleteConsumerGroup(name)
            updater.invokeRefreshModel(consumerGroupsModel)
        }
    }

    fun getCachedConsumerGroup(consumerGroup: String): ConsumerGroupPresentable? {
        return consumerGroupsModel.data?.firstOrNull { it.consumerGroup == consumerGroup }
    }

    companion object {
        fun getInstance(
            connectionId: String,
            project: Project
        ) = (DriverManager.getDriverById(project, connectionId) as? KafkaDriver)?.dataManager

        fun List<KafkaSchemaInfo>.sortedSchemas(connectionId: String): List<KafkaSchemaInfo> {
            val kafkaSettings = KafkaToolWindowSettings.getInstance()
            val config = kafkaSettings.getOrCreateConfig(connectionId)
            val schemas = this.map { schema -> schema.copy(isFavorite = config.schemasPined.contains(schema.name)) }

            val finalSchemas = if (kafkaSettings.showFavoriteSchema) schemas.filter { it.isFavorite } else schemas
            // sort firstly by favorite schema then by name
            return finalSchemas.sortedWith(compareByDescending<KafkaSchemaInfo> { it.isFavorite }.thenBy { it.name.lowercase() })
        }
    }
}