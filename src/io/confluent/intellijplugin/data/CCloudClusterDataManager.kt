package io.confluent.intellijplugin.data

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import io.confluent.intellijplugin.ccloud.cache.DataPlaneCache
import io.confluent.intellijplugin.ccloud.model.Cluster
import io.confluent.intellijplugin.ccloud.model.response.CreateTopicRequest
import io.confluent.intellijplugin.ccloud.model.response.RegisterSchemaRequest
import io.confluent.intellijplugin.ccloud.model.response.SchemaData
import io.confluent.intellijplugin.ccloud.model.response.SchemaEnrichmentResult
import io.confluent.intellijplugin.ccloud.model.response.SchemaReferenceResponse
import io.confluent.intellijplugin.ccloud.model.response.TopicData
import io.confluent.intellijplugin.ccloud.model.response.TopicEnrichmentResult
import io.confluent.intellijplugin.ccloud.model.response.toPresentable
import io.confluent.intellijplugin.ccloud.model.response.toSchemaReferences
import io.confluent.intellijplugin.client.KafkaConstants
import io.confluent.intellijplugin.common.models.RegistrySchemaInEditor
import io.confluent.intellijplugin.core.monitoring.data.storage.ObjectDataModelStorage
import io.confluent.intellijplugin.consumer.editor.KafkaConsumerSettings
import io.confluent.intellijplugin.core.monitoring.data.storage.RootDataModelStorage
import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import io.confluent.intellijplugin.core.rfs.driver.SafeExecutor
import io.confluent.intellijplugin.core.rfs.util.RfsNotificationUtils
import io.confluent.intellijplugin.core.util.asSilent
import io.confluent.intellijplugin.core.util.runAsyncSuspend
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import io.confluent.intellijplugin.model.BdtTopicPartition
import io.confluent.intellijplugin.model.ConsumerGroupOffsetInfo
import io.confluent.intellijplugin.model.ConsumerGroupPresentable
import io.confluent.intellijplugin.model.TopicConfig
import io.confluent.intellijplugin.model.TopicPresentable
import io.confluent.intellijplugin.registry.KafkaRegistryFormat
import io.confluent.intellijplugin.registry.KafkaRegistryType
import io.confluent.intellijplugin.registry.KafkaRegistryUtil
import io.confluent.intellijplugin.registry.SchemaVersionInfo
import io.confluent.intellijplugin.registry.common.KafkaSchemaInfo
import io.confluent.intellijplugin.rfs.ConfluentConnectionData
import io.confluent.intellijplugin.toolwindow.config.KafkaToolWindowSettings
import kotlin.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.asPromise
import java.util.concurrent.ConcurrentHashMap

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
    private val partitionEnrichmentJobs = ConcurrentHashMap<String, Job>()
    private var topicEnrichmentJob: Job? = null
    private var schemaEnrichmentJob: Job? = null
    private val partitionCache = ConcurrentHashMap<String, List<BdtTopicPartition>>()
    private val schemaVersionCache = ConcurrentHashMap<Pair<String, Long>, SchemaVersionInfo>()
    private val schemaTypeCache = ConcurrentHashMap<String, KafkaRegistryFormat>()

    private fun invalidateSchemaVersionCache(schemaName: String) {
        schemaVersionCache.keys.removeIf { it.first == schemaName }
        schemaTypeCache.remove(schemaName)
    }

    /** Clear all cached schema versions (called during refresh). */
    fun clearAllVersionCaches() {
        schemaVersionCache.clear()
        schemaTypeCache.clear()
    }

    override val connectionId: String = cluster.id

    override val connectionData: ConfluentConnectionData
        get() = orgManager.connectionData

    override val client
        get() = orgManager.client

    override val registryType: KafkaRegistryType
        get() = if (dataPlaneCache.hasSchemaRegistry()) KafkaRegistryType.CONFLUENT
        else KafkaRegistryType.NONE

    /**
     * Get the RFS path for a schema subject in CCloud format: [schemaRegistryId, schemaName]
     */
    override fun getSchemaPath(schemaName: String): RfsPath {
        val srId = dataPlaneCache.getSchemaRegistryId()
            ?: throw IllegalStateException(KafkaMessagesBundle.message("error.schema.registry.id.not.available"))
        return RfsPath(listOf(srId, schemaName), isDirectory = false)
    }

    /**
     * Override to use Schema Registry ID for config key, ensuring all clusters sharing an SR see the same config.
     */
    override fun getSchemaRegistryConfigId(): String {
        return dataPlaneCache.getSchemaRegistryId() ?: connectionId
    }

    init {
        // Force initialization of lazy storages so they register with updater before first refresh
        schemaVersionModels
        topicPartitionsModels
        topicConfigsModels

        RootDataModelStorage(
            updater,
            listOfNotNull(topicModel, schemaRegistryModel)
        ).also { Disposer.register(this, it) }
    }

    /**
     * Executes the given action on the EDT, but only if this data manager is not disposed.
     * This prevents queued EDT callbacks from running after disposal during shutdown.
     */
    private fun invokeLaterIfNotDisposed(action: () -> Unit) {
        ApplicationManager.getApplication().invokeLater(
            { action() },
            { Disposer.isDisposed(this@CCloudClusterDataManager) }
        )
    }

    override fun createTopicPartitionsStorage() = ObjectDataModelStorage<String, BdtTopicPartition>(
        updater,
        BdtTopicPartition::partitionId,
        dependOn = null
    ) { topicName ->
        val cached = partitionCache[topicName]
        if (cached != null) {
            return@ObjectDataModelStorage cached
        }

        val existingJob = partitionEnrichmentJobs[topicName]
        if (existingJob != null && existingJob.isActive) {
            return@ObjectDataModelStorage partitionCache[topicName] ?: emptyList()
        }

        try {
            val quickPartitions = runBlockingMaybeCancellable {
                dataPlaneCache.getTopicPartitionsQuick(topicName)
            }
            partitionCache[topicName] = quickPartitions

            if (quickPartitions.isNotEmpty()) {
                val job = driver.coroutineScope.launch(Dispatchers.IO) {
                    try {
                        dataPlaneCache.enrichPartitionsProgressively(topicName, quickPartitions)
                            .collect { enrichedPartition ->
                                invokeLaterIfNotDisposed {
                                    val storage = topicPartitionsModels[topicName]
                                    val current = storage.data ?: return@invokeLaterIfNotDisposed
                                    val updated = current.map { p ->
                                        if (p.partitionId == enrichedPartition.partitionId) enrichedPartition else p
                                    }
                                    partitionCache[topicName] = updated
                                    storage.setData(updated)
                                }
                            }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        thisLogger().warn(
                            "Failed to enrich partitions for topic '$topicName' in cluster ${cluster.id}",
                            e
                        )
                    } finally {
                        partitionEnrichmentJobs.remove(topicName)
                    }
                }
                partitionEnrichmentJobs[topicName] = job
            }

            quickPartitions
        } catch (e: Exception) {
            thisLogger().warn("Failed to load partitions for topic '$topicName' in cluster ${cluster.id}", e)
            partitionCache.remove(topicName)
            emptyList()
        }
    }

    override suspend fun loadTopics(): List<TopicPresentable> = withContext(Dispatchers.IO) {
        try {
            val topics = dataPlaneCache.getTopics().ifEmpty {
                dataPlaneCache.refreshTopics()
            }
            topics.map { it.toPresentable() }
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
            val allTopicData = dataPlaneCache.getTopics()
            if (allTopicData.isEmpty()) {
                return@withContext topics to null
            }

            topicEnrichmentJob?.cancel()
            topicEnrichmentJob = null

            val topicNamesToEnrich = topics.map { it.name }.toSet()
            val topicsToEnrich = allTopicData.filter { it.topicName in topicNamesToEnrich }

            topicEnrichmentJob = driver.coroutineScope.launch(Dispatchers.IO) {
                dataPlaneCache.enrichTopicsDataProgressively(topicsToEnrich)
                    .collect { result ->
                        when (result) {
                            is TopicEnrichmentResult.Success -> {
                                invokeLaterIfNotDisposed {
                                    val current = topicModel.data ?: return@invokeLaterIfNotDisposed
                                    val updated = current.map { topic ->
                                        if (topic.name == result.topicName) {
                                            topic.copy(messageCount = result.data.messageCount)
                                        } else {
                                            topic
                                        }
                                    }
                                    topicModel.setData(updated)
                                }
                            }

                            is TopicEnrichmentResult.Failure -> {
                                thisLogger().warn("Failed to enrich topic ${result.topicName}: ${result.error.message}")
                            }
                        }
                    }
            }
            topics to null
        } catch (t: Throwable) {
            thisLogger().warn("Failed to load detailed topic info for cluster ${cluster.id}", t)
            topics to t
        }
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

    override suspend fun listSchemasNames(limit: Int?, filter: String?): Pair<List<KafkaSchemaInfo>, Boolean> =
        withContext(Dispatchers.IO) {
            if (!dataPlaneCache.hasSchemaRegistry()) {
                return@withContext emptyList<KafkaSchemaInfo>() to false
            }

            try {
                val schemas = dataPlaneCache.getSchemas().ifEmpty {
                    dataPlaneCache.refreshSchemas()
                }
                // Use SR config (not cluster config) so all clusters sharing an SR see the same favorites
                val config = KafkaToolWindowSettings.getInstance().getOrCreateConfig(getSchemaRegistryConfigId())

                var result = schemas.map { schemaData ->
                    KafkaSchemaInfo(
                        name = schemaData.name,
                        type = KafkaRegistryFormat.fromSchemaType(schemaData.schemaType),
                        version = schemaData.latestVersion?.toLong(),
                        compatibility = schemaData.compatibility,
                        isFavorite = config?.schemasPined?.contains(schemaData.name) == true
                    )
                }

                if (!filter.isNullOrBlank()) {
                    result = result.filter { it.name.contains(filter, ignoreCase = true) }
                }

                result to false
            } catch (e: Exception) {
                thisLogger().warn("Failed to list schemas for cluster ${cluster.id}", e)
                emptyList<KafkaSchemaInfo>() to false
            }
        }

    /** Launch non-blocking schema enrichment job with progressive UI updates. */
    private fun launchSchemaEnrichment(schemas: List<KafkaSchemaInfo>): Job {
        val schemaDataList = schemas.map { schema ->
            SchemaData(
                name = schema.name,
                latestVersion = schema.version?.toInt(),
                schemaType = schema.type?.name
            )
        }

        val startTime = System.currentTimeMillis()
        var successCount = 0

        return driver.coroutineScope.launch(Dispatchers.IO) {
            dataPlaneCache.enrichSchemasProgressively(schemaDataList)
                .collect { result ->
                    val model = schemaRegistryModel ?: return@collect

                    when (result) {
                        is SchemaEnrichmentResult.Success -> {
                            successCount++

                            dataPlaneCache.updateSchemaInCache(result.schemaName, result.data)

                            val schemaType = KafkaRegistryFormat.fromSchemaType(result.data.schemaType)
                            schemaTypeCache[result.schemaName] = schemaType

                            invokeLaterIfNotDisposed {
                                val currentSchemas = model.data ?: return@invokeLaterIfNotDisposed
                                val index = currentSchemas.indexOfFirst { it.name == result.schemaName }
                                if (index == -1) return@invokeLaterIfNotDisposed

                                val updatedSchemas = currentSchemas.mapIndexed { i, schema ->
                                    if (i == index) {
                                        schema.copy(
                                            version = result.data.latestVersion?.toLong(),
                                            type = schemaType
                                        )
                                    } else {
                                        schema
                                    }
                                }

                                model.setData(updatedSchemas)
                            }
                        }

                        is SchemaEnrichmentResult.Failure -> {
                            if (result.error !is CancellationException) {
                                thisLogger().warn(
                                    "CCloud: Schema enrichment failure (${result.progress.first}/${result.progress.second}): ${result.schemaName} - ${result.error.message}",
                                    result.error
                                )
                            }
                        }
                    }

                    if (result.progress.first == result.progress.second) {
                        val elapsed = System.currentTimeMillis() - startTime
                        thisLogger().info("CCloud: Schema enrichment completed - success=$successCount, failure=${result.progress.second - successCount}, total=${result.progress.second}, elapsed=${elapsed}ms")
                    }
                }
        }
    }

    override suspend fun updateSchemaList(
        schemas: List<KafkaSchemaInfo>
    ): Pair<List<KafkaSchemaInfo>, Throwable?> = withContext(Dispatchers.IO) {
        if (!dataPlaneCache.hasSchemaRegistry() || schemas.isEmpty()) {
            return@withContext schemas to null
        }

        try {
            schemaEnrichmentJob = launchSchemaEnrichment(schemas)
            schemas to null
        } catch (e: Exception) {
            thisLogger().warn("Failed to update schema list for cluster ${cluster.id}", e)
            schemas to e
        }
    }

    override suspend fun listSchemaVersions(schemaName: String): List<Long> = withContext(Dispatchers.IO) {
        if (!dataPlaneCache.hasSchemaRegistry()) {
            return@withContext emptyList()
        }

        try {
            dataPlaneCache.getFetcher()?.listSchemaVersions(schemaName) ?: emptyList()
        } catch (e: Exception) {
            thisLogger().warn("Failed to list schema versions for '$schemaName' in cluster ${cluster.id}", e)
            emptyList()
        }
    }

    override suspend fun loadConsumerGroupOffset(name: String): List<ConsumerGroupOffsetInfo> {
        // TODO: Implement when CCloud REST API supports consumer group offsets
        return emptyList()
    }

    override suspend fun loadTopicInfo(name: String): TopicPresentable = withContext(Dispatchers.IO) {
        val topics = dataPlaneCache.getTopics()
        topics.find { it.topicName == name }?.toPresentable()
            ?: throw IllegalArgumentException(KafkaMessagesBundle.message("error.topic.not.found", name))
    }

    override suspend fun resetOffsets(
        consumeGroupId: String,
        offsets: Map<TopicPartition, OffsetAndMetadata>
    ) {
        // TODO: Implement when CCloud REST API supports consumer group offset management
        throw UnsupportedOperationException("Reset offsets not supported for Confluent Cloud")
    }

    override suspend fun getOffsetsForData(
        partitions: Set<TopicPartition>,
        timestamp: Long
    ): Map<TopicPartition, Long> {
        // TODO: Implement when CCloud REST API supports offset queries by timestamp
        throw UnsupportedOperationException("Get offsets for timestamp not supported for Confluent Cloud")
    }

    @RequiresBackgroundThread
    override fun getSchemasForEditor(): List<RegistrySchemaInEditor> {
        if (!dataPlaneCache.hasSchemaRegistry()) {
            return emptyList()
        }

        return try {
            val schemas = getSchemas()
            schemas.map { schema ->
                val schemaFormat = schema.type
                    ?: runBlockingMaybeCancellable { getCachedOrLoadSchemaType(schema.name) }
                    ?: KafkaRegistryFormat.UNKNOWN
                RegistrySchemaInEditor(
                    schemaName = schema.name,
                    schemaFormat = schemaFormat
                )
            }.sorted()
        } catch (e: ProcessCanceledException) {
            throw e  // Re-throw cancellation to preserve IDE cancellation semantics
        } catch (e: CancellationException) {
            throw e  // Re-throw coroutine cancellation
        } catch (e: Exception) {
            thisLogger().warn("Failed to get schemas for editor in cluster ${cluster.id}", e)
            emptyList()
        }
    }

    private suspend fun getCachedOrLoadSchemaType(name: String): KafkaRegistryFormat? =
        schemaTypeCache[name] ?: getCachedOrLoadSchema(name).type?.also {
            schemaTypeCache[name] = it
        }

    override suspend fun getLatestVersionInfo(schemaName: String): SchemaVersionInfo? {
        if (!dataPlaneCache.hasSchemaRegistry()) {
            return null
        }

        return try {
            withContext(Dispatchers.IO) {
                val versionResponse = dataPlaneCache.getFetcher()?.getLatestVersionInfo(schemaName)
                versionResponse?.let {
                    SchemaVersionInfo(
                        schemaName = it.subject,
                        version = it.version.toLong(),
                        type = KafkaRegistryFormat.fromSchemaType(it.schemaType),
                        schema = it.schema,
                        references = it.references.toSchemaReferences()
                    )
                }
            }
        } catch (e: Exception) {
            thisLogger().warn("Failed to get latest version info for '$schemaName' in cluster ${cluster.id}", e)
            null
        }
    }

    override fun getSchemaVersionInfo(schemaName: String, version: Long): Promise<SchemaVersionInfo> = runAsyncSuspend {
        if (!dataPlaneCache.hasSchemaRegistry()) {
            error(KafkaMessagesBundle.message("error.schema.registry.not.configured.for.cluster"))
        }

        val cacheKey = schemaName to version
        schemaVersionCache[cacheKey]?.let {
            return@runAsyncSuspend it
        }
        try {
            withContext(Dispatchers.IO) {
                val versionResponse = dataPlaneCache.getFetcher()?.getSchemaVersionInfo(schemaName, version)
                    ?: error(KafkaMessagesBundle.message("error.failed.to.fetch.schema.version.info"))

                val schemaVersionInfo = SchemaVersionInfo(
                    schemaName = versionResponse.subject,
                    version = versionResponse.version.toLong(),
                    type = KafkaRegistryFormat.fromSchemaType(versionResponse.schemaType),
                    schema = versionResponse.schema,
                    references = versionResponse.references.toSchemaReferences()
                )
                schemaVersionCache[cacheKey] = schemaVersionInfo
                schemaVersionInfo
            }
        } catch (e: Exception) {
            thisLogger().warn(
                "Failed to get schema version info for '$schemaName' version $version in cluster ${cluster.id}",
                e
            )
            throw e
        }
    }

    override fun parseSchemaForDisplay(versionInfo: SchemaVersionInfo): Result<io.confluent.kafka.schemaregistry.ParsedSchema> {
        // CCloud doesn't need the registry client for parsing - parse schemas standalone
        return KafkaRegistryUtil.parseSchema(
            versionInfo.type,
            versionInfo.schema,
            client = null,
            versionInfo.references
        )
    }

    override suspend fun getCachedOrLoadSchema(name: String): KafkaSchemaInfo {
        if (!dataPlaneCache.hasSchemaRegistry()) {
            throw UnsupportedOperationException("Schema registry not configured for this cluster")
        }

        // Check if already cached with full metadata
        val cached = getCachedSchema(name)
        if (cached != null && cached.type != null) {
            return cached
        }

        // Load from API if not cached or incomplete
        return try {
            withContext(Dispatchers.IO) {
                val schemaData = dataPlaneCache.getFetcher()?.loadSchemaInfo(name)
                // Use SR config (not cluster config) so all clusters sharing an SR see the same favorites
                val config = KafkaToolWindowSettings.getInstance().getOrCreateConfig(getSchemaRegistryConfigId())

                schemaData?.let {
                    KafkaSchemaInfo(
                        name = it.name,
                        type = KafkaRegistryFormat.fromSchemaType(it.schemaType),
                        version = it.latestVersion?.toLong(),
                        isFavorite = config?.schemasPined?.contains(it.name) == true
                    )
                } ?: throw IllegalArgumentException(KafkaMessagesBundle.message("error.schema.not.found", name))
            }
        } catch (e: Exception) {
            thisLogger().warn("Failed to load schema '$name' in cluster ${cluster.id}", e)
            throw e
        }
    }

    // Schema write operations

    override fun updateSchema(versionInfo: SchemaVersionInfo, newSchema: String): Promise<Unit> =
        SafeExecutor.instance.asyncSuspend(
            taskName = null,
            timeout = Duration.INFINITE
        ) {
            if (!dataPlaneCache.hasSchemaRegistry()) {
                error(KafkaMessagesBundle.message("error.schema.registry.not.configured.for.cluster"))
            }

            val parsedSchema = KafkaRegistryUtil.parseSchema(
                versionInfo.type,
                newSchema,
                client = null,
                versionInfo.references
            ).getOrThrow()

            val request = RegisterSchemaRequest(
                schema = parsedSchema.canonicalString(),
                schemaType = versionInfo.type.name,
                references = versionInfo.references.map { ref ->
                    SchemaReferenceResponse(
                        name = ref.name,
                        subject = ref.subject,
                        version = ref.version
                    )
                }.takeIf { it.isNotEmpty() }
            )

            dataPlaneCache.createSchema(versionInfo.schemaName, request)

            invalidateSchemaVersionCache(versionInfo.schemaName)
            updateSingleSchemaInList(versionInfo.schemaName)
            schemaVersionModels[versionInfo.schemaName]?.let { updater.invokeRefreshModel(it) }
            Unit
        }.deferred.asCompletableFuture().asPromise().asSilent()

    override fun deleteRegistrySchemaVersion(versionInfo: SchemaVersionInfo) {
        driver.coroutineScope.launch {
            try {
                if (!dataPlaneCache.hasSchemaRegistry()) {
                    error(KafkaMessagesBundle.message("error.schema.registry.not.configured.for.cluster"))
                }

                withContext(Dispatchers.IO) {
                    val fetcher = dataPlaneCache.getFetcher()
                        ?: error(KafkaMessagesBundle.message("error.schema.registry.fetcher.not.available"))
                    fetcher.deleteSchemaVersion(versionInfo.schemaName, versionInfo.version, permanent = false)
                }

                invalidateSchemaVersionCache(versionInfo.schemaName)
                schemaVersionModels[versionInfo.schemaName]?.let { updater.invokeRefreshModel(it) }
                updateSingleSchemaInList(versionInfo.schemaName)
            } catch (t: Throwable) {
                RfsNotificationUtils.showExceptionMessage(project, t)
            }
        }
    }

    fun deleteSchema(schemaName: String) {
        driver.coroutineScope.launch {
            try {
                if (!dataPlaneCache.hasSchemaRegistry()) {
                    error(KafkaMessagesBundle.message("error.schema.registry.not.configured.for.cluster"))
                }

                val registryInfo = getCachedSchema(schemaName) ?: return@launch
                val confirmed = if (registryInfo.isSoftDeleted) {
                    withContext(Dispatchers.EDT) {
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
                        ) == Messages.OK
                    }
                } else {
                    withContext(Dispatchers.EDT) {
                        Messages.showOkCancelDialog(
                            project,
                            KafkaMessagesBundle.message(
                                "action.remove.schema.confirm.dialog.msg.soft",
                                registryInfo.name
                            ),
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

    private suspend fun deleteSchemaWithoutConfirmation(schemaName: String, permanent: Boolean) {
        withContext(Dispatchers.IO) {
            dataPlaneCache.deleteSchema(schemaName, permanent)
        }
        invalidateSchemaVersionCache(schemaName)
        removeSingleSchemaFromList(schemaName)
    }

    fun createSchema(schemaName: String, parsedSchema: io.confluent.kafka.schemaregistry.ParsedSchema) =
        runAsyncSuspend {
            if (!dataPlaneCache.hasSchemaRegistry()) {
                error(KafkaMessagesBundle.message("error.schema.registry.not.configured.for.cluster"))
            }

            withContext(Dispatchers.IO) {
                val request = RegisterSchemaRequest(
                    schema = parsedSchema.canonicalString(),
                    schemaType = parsedSchema.schemaType()
                )
                dataPlaneCache.createSchema(schemaName, request)
            }
            updateSingleSchemaInList(schemaName)
        }

    private suspend fun updateSingleSchemaInList(schemaName: String) {
        try {
            val updatedSchemaData = withContext(Dispatchers.IO) {
                dataPlaneCache.getFetcher()?.loadSchemaInfo(schemaName)
            } ?: return

            val config = KafkaToolWindowSettings.getInstance().getOrCreateConfig(getSchemaRegistryConfigId())

            val updatedSchema = KafkaSchemaInfo(
                name = updatedSchemaData.name,
                type = KafkaRegistryFormat.fromSchemaType(updatedSchemaData.schemaType),
                version = updatedSchemaData.latestVersion?.toLong(),
                isFavorite = config.schemasPined.contains(updatedSchemaData.name)
            )

            withContext(Dispatchers.Default) {
                val currentSchemas = schemaRegistryModel?.data ?: emptyList()
                val existingIndex = currentSchemas.indexOfFirst { it.name == schemaName }

                val updatedSchemas = if (existingIndex >= 0) {
                    currentSchemas.toMutableList().apply { set(existingIndex, updatedSchema) }
                } else {
                    currentSchemas + updatedSchema
                }

                val sortedSchemas = sortSchemasWithFavorites(
                    updatedSchemas,
                    pinnedSchemas = config?.schemasPined ?: emptySet(),
                    showFavoriteOnly = KafkaToolWindowSettings.getInstance().showFavoriteSchema
                )

                // Apply limit to respect user's limit setting
                val (limitedSchemas, _) = applySchemaLimit(sortedSchemas, config?.registryLimit)

                schemaRegistryModel?.setData(limitedSchemas)
            }
        } catch (e: Exception) {
            thisLogger().warn("Failed to update single schema '$schemaName', falling back to full refresh", e)
            schemaRegistryModel?.let { updater.invokeRefreshModel(it) }
        }
    }

    private suspend fun removeSingleSchemaFromList(schemaName: String) {
        try {
            withContext(Dispatchers.Default) {
                val currentSchemas = schemaRegistryModel?.data ?: emptyList()
                val updatedSchemas = currentSchemas.filterNot { it.name == schemaName }
                schemaRegistryModel?.setData(updatedSchemas)
            }
        } catch (e: Exception) {
            thisLogger().warn("Failed to remove schema '$schemaName' from list", e)
            schemaRegistryModel?.let { updater.invokeRefreshModel(it) }
        }
    }

    @RequiresBackgroundThread
    override fun loadTopicNames(): List<TopicPresentable> = getTopics()

    override suspend fun createTopic(
        name: String,
        partitions: Int?,
        replicationFactor: Int?,
        configs: Map<String, String>
    ): Result<Unit> {
        return try {
            val partitionCount = partitions ?: KafkaConstants.DEFAULT_CCLOUD_PARTITION_COUNT

            val createdTopicData = withContext(Dispatchers.IO) {
                val request = CreateTopicRequest(
                    topicName = name,
                    partitionsCount = partitionCount,
                    replicationFactor = replicationFactor,
                    configs = configs.map { (k, v) ->
                        CreateTopicRequest.ConfigEntry(k, v)
                    }.ifEmpty { null }
                )
                dataPlaneCache.createTopic(request)
            }

            updateSingleTopicInList(createdTopicData)
            Result.success(Unit)
        } catch (e: Exception) {
            thisLogger().warn("Failed to create topic '$name'", e)
            invokeLaterIfNotDisposed {
                updater.invokeRefreshModel(topicModel)
            }
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

            removeSingleTopicFromList(topicNames)
            Result.success(Unit)
        } catch (e: Exception) {
            thisLogger().warn("Failed to delete topics: $topicNames", e)
            invokeLaterIfNotDisposed {
                updater.invokeRefreshModel(topicModel)
            }
            Result.failure(e)
        }
    }

    private suspend fun updateSingleTopicInList(topicData: TopicData) {
        try {
            val newTopic = topicData.toPresentable()

            withContext(Dispatchers.Default) {
                val currentTopics = topicModel.data ?: emptyList()
                val config = KafkaToolWindowSettings.getInstance().getOrCreateConfig(connectionId)
                val enrichedTopic = newTopic.copy(isFavorite = config.topicsPined.contains(topicData.topicName))

                val updatedTopics = (currentTopics + enrichedTopic).sortedWith(
                    compareByDescending<TopicPresentable> { it.isFavorite }
                        .thenBy { it.name.lowercase() }
                )

                topicModel.setData(updatedTopics)
            }
        } catch (e: Exception) {
            thisLogger().warn("Failed to add topic '${topicData.topicName}' to list", e)
            invokeLaterIfNotDisposed {
                updater.invokeRefreshModel(topicModel)
            }
        }
    }

    private suspend fun removeSingleTopicFromList(topicNames: List<String>) {
        try {
            withContext(Dispatchers.Default) {
                val currentTopics = topicModel.data ?: emptyList()
                val updatedTopics = currentTopics.filterNot { it.name in topicNames }
                topicModel.setData(updatedTopics)
            }

            topicNames.forEach { partitionCache.remove(it) }
        } catch (e: Exception) {
            thisLogger().warn("Failed to remove topics $topicNames from list", e)
            invokeLaterIfNotDisposed {
                updater.invokeRefreshModel(topicModel)
            }
        }
    }

    override suspend fun clearTopic(topicName: String): Result<Unit> {
        return Result.failure(UnsupportedOperationException("Clear topic not supported for Confluent Cloud"))
    }

    override fun clearPartitions(partitions: List<BdtTopicPartition>) {}
    override fun supportsClearPartitions(): Boolean = false

    override fun supportsInSyncReplicasData(): Boolean = false

    override fun supportsConsumerGroups(): Boolean = false
    override fun presetConnectionTag(): String = "ccloud"
    override fun supportedConsumerProperties(): Set<String> = KafkaConsumerSettings.CCLOUD_PROPERTIES
    fun getDataPlaneCache(): DataPlaneCache = dataPlaneCache

    /** Cancel all ongoing enrichment jobs and clear partition cache. Called during refresh. */
    fun cancelAllEnrichmentJobs() {
        topicEnrichmentJob?.cancel()
        schemaEnrichmentJob?.cancel()
        partitionEnrichmentJobs.values.forEach { it.cancel() }
        partitionEnrichmentJobs.clear()
        partitionCache.clear()
    }

    override fun dispose() {
        cancelAllEnrichmentJobs()
        partitionEnrichmentJobs.clear()
        partitionCache.clear()
        schemaVersionCache.clear()
        schemaTypeCache.clear()
    }
}
