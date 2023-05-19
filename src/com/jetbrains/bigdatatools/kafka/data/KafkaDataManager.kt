package com.jetbrains.bigdatatools.kafka.data

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.jetbrains.bigdatatools.common.connection.updater.IntervalUpdateSettings
import com.jetbrains.bigdatatools.common.monitoring.data.MonitoringDataManager
import com.jetbrains.bigdatatools.common.monitoring.data.model.FieldGroupsData
import com.jetbrains.bigdatatools.common.monitoring.data.model.ObjectDataModel
import com.jetbrains.bigdatatools.common.monitoring.data.storage.FieldGroupsDataModelStorage
import com.jetbrains.bigdatatools.common.monitoring.data.storage.ObjectDataModelStorage
import com.jetbrains.bigdatatools.common.monitoring.data.storage.RootDataModelStorage
import com.jetbrains.bigdatatools.common.rfs.driver.manager.DriverManager
import com.jetbrains.bigdatatools.common.rfs.util.RfsNotificationUtils
import com.jetbrains.bigdatatools.common.util.executeOnPooledThread
import com.jetbrains.bigdatatools.common.util.runAsync
import com.jetbrains.bigdatatools.common.util.runAsyncSuspend
import com.jetbrains.bigdatatools.kafka.client.KafkaClient
import com.jetbrains.bigdatatools.kafka.common.models.RegistrySchemaInEditor
import com.jetbrains.bigdatatools.kafka.consumer.editor.KafkaConsumerPanelStorage
import com.jetbrains.bigdatatools.kafka.model.BdtTopicPartition
import com.jetbrains.bigdatatools.kafka.model.ConsumerGroupPresentable
import com.jetbrains.bigdatatools.kafka.model.TopicConfig
import com.jetbrains.bigdatatools.kafka.model.TopicPresentable
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryType
import com.jetbrains.bigdatatools.kafka.registry.SchemaVersionInfo
import com.jetbrains.bigdatatools.kafka.registry.common.KafkaSchemaInfo
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver
import com.jetbrains.bigdatatools.kafka.statistics.KafkaUsagesCollector
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import io.confluent.kafka.schemaregistry.ParsedSchema
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.await
import software.amazon.awssdk.services.glue.model.Compatibility
import java.util.concurrent.TimeUnit

class KafkaDataManager(project: Project?,
                       val connectionData: KafkaConnectionData,
                       settings: IntervalUpdateSettings) : MonitoringDataManager(project, settings) {
  val registryType = connectionData.registryType

  val connectionId = connectionData.innerId
  override val client = KafkaClient(project, connectionData, false).also { Disposer.register(this, it) }
  val consumerPanelStorage = KafkaConsumerPanelStorage(this).also { Disposer.register(this, it) }


  internal val topicModel = createTopicsDataModel().also { Disposer.register(this, it) }

  internal val consumerGroupsModel = createConsumerGroupsDataModel().also { Disposer.register(this, it) }
  var topicPartitionsModels = createTopicPartitionsStorage().also { Disposer.register(this, it) }
  val topicConfigsModels = createTopicConfigsStorage().also { Disposer.register(this, it) }

  private val schemaVersionModels = createSchemaVersionsStorage().also { Disposer.register(this, it) }


  internal val schemaRegistryModel = createSchemaRegistryDataModel()?.also {
    Disposer.register(this, it)
  }

  init {
    init()
    RootDataModelStorage(updater, listOfNotNull(topicModel,
                                                schemaRegistryModel,
                                                consumerGroupsModel)).also { Disposer.register(this, it) }
  }

  fun getTopics() = topicModel.data ?: emptyList()

  fun createTopic(name: String, numPartition: Int?, replicaFactor: Int?) = actionWrapper {
    client.createTopic(name, numPartition, replicaFactor)
    updater.invokeRefreshModel(topicModel)

    KafkaUsagesCollector.topicCreatedEvent.log(project)
  }

  fun initRefreshSchemasIfRequired() {
    val schemaModel = schemaRegistryModel
    if (schemaModel?.isInitedByFirstTime == false) {
      updater.invokeRefreshModel(schemaModel)
    }
  }

  fun getSchemasForEditor() = schemaRegistryModel?.data?.map {
    RegistrySchemaInEditor(schemaName = it.name)
  }?.sorted() ?: emptyList()

  fun deleteTopic(topicNames: List<String>) = actionWrapper {
    topicNames.forEach {
      client.deleteTopic(it)
    }

    updater.invokeRefreshModel(topicModel)

    KafkaUsagesCollector.topicDeletedEvent.log(project)
  }

  private fun createTopicsDataModel() = ObjectDataModel(TopicPresentable::name, additionalInfoLoading = { model ->
    try {
      runAsyncSuspend {
        client.getDetailedTopicsInfo(model.data?.map { it.name } ?: emptyList())
      }.blockingGet(20, TimeUnit.SECONDS) ?: emptyList()
    }
    catch (t: Throwable) {
      thisLogger().warn(t)
      model.data ?: emptyList()
    }
  }) {
    val toolWindowSettings = KafkaToolWindowSettings.getInstance()
    val topicFilterName = toolWindowSettings.getOrCreateConfig(connectionId).topicFilterName
    val topics = client.getTopics(toolWindowSettings.showInternalTopics).filter {
      topicFilterName == null || it.name.contains(topicFilterName)
    }
    val topicLimit = KafkaToolWindowSettings.getInstance().getOrCreateConfig(connectionId).topicLimit
    (topicLimit?.let { topics.take(it) } ?: topics) to (topicLimit != null && topics.size > topicLimit)
  }

  private fun createConsumerGroupsDataModel() =
    ObjectDataModel(ConsumerGroupPresentable::consumerGroup, additionalInfoLoading = { dataObject ->
      try {
        val groupIds = dataObject.data?.map { it.consumerGroup } ?: emptyList()
        client.getDetailedConsumerGroups(groupIds)
      }
      catch (t: Throwable) {
        thisLogger().warn(t)
        dataObject.data ?: emptyList()
      }
    }) {
      val toolWindowSettings = KafkaToolWindowSettings.getInstance()
      val filterName = toolWindowSettings.getOrCreateConfig(connectionId).consumerFilterName

      val groups = client.getConsumerGroups().filter {
        filterName == null || it.consumerGroup.contains(filterName)
      }
      val limit = KafkaToolWindowSettings.getInstance().getOrCreateConfig(connectionId).consumerLimit
      (limit?.let { groups.take(it) } ?: groups) to (limit != null && groups.size > limit)
    }

  private fun actionWrapper(body: () -> Unit) = executeOnPooledThread {
    try {
      body()
    }
    catch (t: Throwable) {
      RfsNotificationUtils.showExceptionMessage(project, t)
    }
  }


  private fun createTopicPartitionsStorage() = ObjectDataModelStorage<String, BdtTopicPartition>(updater,
                                                                                                 BdtTopicPartition::partitionId,
                                                                                                 dependOn = topicModel) { topicName ->
    val topics = topicModel.data ?: emptyList()
    val topic = topics.find { it.name == topicName } ?: error(KafkaMessagesBundle.message("topic.not.found", topicName))
    topic.partitionList
  }

  private fun createTopicConfigsStorage() = ObjectDataModelStorage<String, TopicConfig>(updater,
                                                                                        TopicConfig::name) { topicName ->
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
    client.confluentRegistryClient?.deleteSchemaVersion(versionSchema) ?: client.glueRegistryClient?.deleteSchemaVersion(versionSchema)
    updater.invokeRefreshModel(schemaVersionModels[versionSchema.schemaName])
    schemaRegistryModel?.let { updater.invokeRefreshModel(it) }

  }

  fun updateSchema(versionInfo: SchemaVersionInfo, newText: String) = runAsync {
    client.confluentRegistryClient?.updateSchema(versionInfo, newText) ?: client.glueRegistryClient?.updateSchema(versionInfo, newText)
    ?: error("Not Fond registry")
    schemaRegistryModel?.let { updater.invokeRefreshModel(it) }
    updater.invokeRefreshModel(schemaVersionModels[versionInfo.schemaName])
  }

  fun deleteSchema(info: KafkaSchemaInfo) = actionWrapper {
    client.confluentRegistryClient?.deleteSchema(info.name, false) ?: client.glueRegistryClient?.deleteSchema(info.name)
    schemaRegistryModel?.let { updater.invokeRefreshModel(it) }
  }

  fun createSchema(schemaName: String, parsedSchema: ParsedSchema) = runAsync {
    client.confluentRegistryClient?.createSchema(schemaName, parsedSchema)
    ?: client.glueRegistryClient?.createSchema(schemaName,
                                               parsedSchema.schemaType(),
                                               parsedSchema.canonicalString(), Compatibility.BACKWARD, "",
                                               emptyMap())
    schemaRegistryModel?.let { updater.invokeRefreshModel(it) }
  }

  private fun createSchemaRegistryDataModel(): ObjectDataModel<KafkaSchemaInfo>? {
    if (registryType == KafkaRegistryType.NONE)
      return null
    val dataModel = ObjectDataModel(KafkaSchemaInfo::name, additionalInfoLoading = {
      runAsyncSuspend {
        updateSchemaList(it)
      }.blockingGet(20, TimeUnit.SECONDS) ?: emptyList()
    }) {
      val filter = KafkaToolWindowSettings.getInstance().getOrCreateConfig(connectionId).schemaFilterName
      val limit = KafkaToolWindowSettings.getInstance().getOrCreateConfig(connectionId).registryLimit
      listSchemas(limit, filter)

    }
    return dataModel
  }

  private fun listSchemas(limit: Int?, filter: String?, registryShowDeletedSubjects: Boolean = false) =
    client.confluentRegistryClient?.listSchemas(limit, filter, registryShowDeletedSubjects)
    ?: client.glueRegistryClient?.listSchemas(limit, filter)
    ?: (emptyList<KafkaSchemaInfo>() to false)

  private suspend fun updateSchemaList(dataModel: ObjectDataModel<KafkaSchemaInfo>): List<KafkaSchemaInfo> {
    val data = dataModel.data ?: emptyList()

    val loadedInfo = data.map {
      runAsyncSuspend {
        try {
          loadSchema(it.name)
        }
        catch (t: Throwable) {
          thisLogger().warn(t)
          it
        }
      }
    }.map { it.await() }
    return loadedInfo
  }

  private fun loadSchema(schemaName: String) =
    client.confluentRegistryClient?.loadSchemaInfo(schemaName) ?: client.glueRegistryClient?.loadSchemaInfo(schemaName) ?: error("Not used")

  fun isSchemaExists(name: String) = getCachedSchema(name) != null

  fun getCachedOrLoadSchema(name: String): KafkaSchemaInfo = getCachedSchema(name)?.takeIf { it.type != null } ?: loadSchema(name)

  fun getCachedSchema(name: String) = schemaRegistryModel?.data?.firstOrNull { it.name == name }

  fun getLatestVersionInfo(schemaName: String) =
    client.confluentRegistryClient?.getLatestVersionInfo(schemaName) ?: client.glueRegistryClient?.getLatestVersionInfo(schemaName)

  companion object {
    fun getInstance(connectionId: String,
                    project: Project) = (DriverManager.getDriverById(project, connectionId) as? KafkaDriver)?.dataManager
  }
}