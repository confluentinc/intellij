package com.jetbrains.bigdatatools.kafka.data

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.jetbrains.bigdatatools.common.connection.updater.IntervalUpdateSettings
import com.jetbrains.bigdatatools.common.monitoring.data.MonitoringDataManager
import com.jetbrains.bigdatatools.common.monitoring.data.model.ObjectDataModel
import com.jetbrains.bigdatatools.common.monitoring.data.storage.ObjectDataModelStorage
import com.jetbrains.bigdatatools.common.monitoring.data.storage.RootDataModelStorage
import com.jetbrains.bigdatatools.common.rfs.driver.manager.DriverManager
import com.jetbrains.bigdatatools.common.rfs.util.RfsNotificationUtils
import com.jetbrains.bigdatatools.common.util.executeOnPooledThread
import com.jetbrains.bigdatatools.common.util.runAsync
import com.jetbrains.bigdatatools.common.util.withCatchNotifyErrorDialog
import com.jetbrains.bigdatatools.kafka.client.KafkaClient
import com.jetbrains.bigdatatools.kafka.consumer.editor.KafkaConsumerPanelStorage
import com.jetbrains.bigdatatools.kafka.model.*
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryUtil
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver
import com.jetbrains.bigdatatools.kafka.statistics.KafkaUsagesCollector
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import io.confluent.kafka.schemaregistry.ParsedSchema

class KafkaDataManager(project: Project?,
                       val connectionData: KafkaConnectionData,
                       settings: IntervalUpdateSettings) : MonitoringDataManager(project, settings) {
  val connectionId = connectionData.innerId
  override val client = KafkaClient(project, connectionData, false).also { Disposer.register(this, it) }
  val consumerPanelStorage = KafkaConsumerPanelStorage(this).also { Disposer.register(this, it) }

  val isKafkaRegistryEnabled = connectionData.registryUrl?.isNotBlank() == true

  internal val schemaRegistryModel = createSchemaRegistryDataModel().also {
    it?.let { dataModel -> Disposer.register(this, dataModel) }
  }

  internal val topicModel = createTopicsDataModel().also { Disposer.register(this, it) }

  internal val consumerGroupsModel = createConsumerGroupsDataModel().also { Disposer.register(this, it) }
  var topicPartitionsModels = createTopicPartitionsStorage().also { Disposer.register(this, it) }
  private val schemaVersionsModels = createSchemeVersionsStorage().also { Disposer.register(this, it) }
  val topicConfigsModels = createTopicConfigsStorage().also { Disposer.register(this, it) }
  private val schemaFieldsModels = createSchemaFieldsStorage().also { Disposer.register(this, it) }


  init {
    init()
    RootDataModelStorage(updater, listOfNotNull(topicModel, consumerGroupsModel, schemaRegistryModel)).also { Disposer.register(this, it) }
  }

  fun getTopics() = topicModel.data ?: emptyList()

  fun createTopic(name: String, numPartition: Int?, replicaFactor: Int?) = actionWrapper {
    client.createTopic(name, numPartition, replicaFactor)
    updater.invokeRefreshModel(topicModel)

    KafkaUsagesCollector.topicCreatedEvent.log(project)
  }

  fun deleteTopic(topicNames: List<String>) = actionWrapper {
    topicNames.forEach {
      client.deleteTopic(it)
    }

    updater.invokeRefreshModel(topicModel)

    KafkaUsagesCollector.topicDeletedEvent.log(project)
  }

  private fun createTopicsDataModel() = ObjectDataModel(TopicPresentable::name) {
    client.getTopics(KafkaToolWindowSettings.getInstance().showInternalTopics)
  }

  private fun createConsumerGroupsDataModel() =
    ObjectDataModel(ConsumerGroupPresentable::consumerGroup) {
      client.getConsumerGroups()
    }

  private fun createSchemaRegistryDataModel(): ObjectDataModel<SchemaRegistryInfo>? {
    if (!isKafkaRegistryEnabled)
      return null

    val dataModel = ObjectDataModel(SchemaRegistryInfo::id) {
      val client = client.registryClient!!
      val subjects = client.getAllSubjects(KafkaToolWindowSettings.getInstance().registryShowDeletedSubjects)

      subjects.map {
        val meta = try {
          client.getLatestSchemaMetadata(it)
        }
        catch (t: Throwable) {
          null
        }
        SchemaRegistryInfo(name = it, meta = meta)
      }
    }
    return dataModel
  }

  fun getRegistrySchemaFieldsModel(id: Int): ObjectDataModel<SchemaRegistryFieldsInfo> = schemaFieldsModels.get(id)
  fun getRegistrySchemaVersionsModel(id: Int): ObjectDataModel<SchemaRegistryInfo> = schemaVersionsModels.get(id)
  fun getSchemaInfo(id: Int) = schemaRegistryModel?.data?.firstOrNull { it.id == id }


  private fun actionWrapper(body: () -> Unit) = executeOnPooledThread {
    try {
      body()
    }
    catch (t: Throwable) {
      RfsNotificationUtils.showExceptionMessage(project, t)
    }
  }

  fun deleteRegistrySchemaVersion(registryInfo: SchemaRegistryInfo, isPermanent: Boolean = false) = executeOnPooledThread {
    withCatchNotifyErrorDialog {
      val name = registryInfo.name
      client.registryClient?.deleteSchemaVersion(name, registryInfo.version.toString(), isPermanent)

      schemaRegistryModel?.let {
        updater.invokeRefreshModel(it)
      }
      updater.invokeRefreshModel(getRegistrySchemaVersionsModel(registryInfo.id))
    }
  }

  private fun deleteRegistrySchema(name: String, isPermanent: Boolean) = executeOnPooledThread {
    withCatchNotifyErrorDialog {
      client.registryClient?.deleteSubject(name, isPermanent)
      schemaRegistryModel?.let { updater.invokeRefreshModel(it) }
    }
  }

  fun deleteRegistrySchema(registryInfo: SchemaRegistryInfo, isPermanent: Boolean) = executeOnPooledThread {
    deleteRegistrySchema(registryInfo.name, isPermanent)
  }

  fun createRegistrySubject(schemaName: String, parsedSchema: ParsedSchema) = runAsync {
    client.registryClient?.register(schemaName, parsedSchema)
    schemaRegistryModel?.let { updater.invokeRefreshModel(it) }
  }

  fun updateSchema(registryInfo: SchemaRegistryInfo, newText: @NlsSafe String) = runAsync {
    val registryClient = client.registryClient ?: return@runAsync
    val parsedSchema = KafkaRegistryUtil.parseSchema(registryInfo, newText)
    registryClient.register(registryInfo.name, parsedSchema)

    schemaRegistryModel?.let {
      updater.invokeRefreshModel(it)
    }
    updater.invokeRefreshModel(getRegistrySchemaVersionsModel(registryInfo.id))
  }

  fun getRegistrySchema(subjectName: String) = schemaRegistryModel?.data?.firstOrNull { it.name == subjectName }

  fun getRegistrySchemaById(id: Int?): ParsedSchema? {
    id ?: return null
    return client.registryClient?.getSchemaById(id)
  }

  private fun createTopicPartitionsStorage() = ObjectDataModelStorage<String, TopicPartition>(updater,
                                                                                              TopicPartition::partitionId,
                                                                                              dependOn = topicModel) { topicName ->
    val topics = topicModel.data ?: emptyList()
    val topic = topics.find { it.name == topicName } ?: error(KafkaMessagesBundle.message("topic.not.found", topicName))
    topic.partitionList
  }

  private fun createSchemeVersionsStorage() = ObjectDataModelStorage<Int, SchemaRegistryInfo>(updater, SchemaRegistryInfo::id) {
    val client = client.registryClient!!
    val meta = getSchemaInfo(it) ?: error("Meta is not found")

    val versions = client.getAllVersions(meta.name) ?: emptyList()
    val metas = versions.map { version ->
      val versionMeta = client.getSchemaMetadata(meta.name, version)
      SchemaRegistryInfo(name = meta.name, meta = versionMeta)
    }
    metas
  }


  private fun createTopicConfigsStorage() = ObjectDataModelStorage<String, TopicConfig>(updater,
                                                                                        TopicConfig::name,
                                                                                        dependOn = topicModel) { topicName ->
    val topics = topicModel.data ?: emptyList()
    val topic = topics.find { it.name == topicName } ?: error(KafkaMessagesBundle.message("topic.not.found", topicName))
    val showFullTopicConfig = KafkaToolWindowSettings.getInstance().showFullTopicConfig

    if (showFullTopicConfig)
      topic.topicConfigs
    else
      topic.topicConfigs.filter { it.value != it.defaultValue }
  }

  private fun createSchemaFieldsStorage() = ObjectDataModelStorage<Int, SchemaRegistryFieldsInfo>(updater, SchemaRegistryFieldsInfo::name) {
    val client = client.registryClient!!
    val meta = getSchemaInfo(it)?.meta ?: error("Meta is not found")

    val schema = try {
      client.parseSchema(meta.schemaType, meta.schema, meta.references).get()
    }
    catch (t: Throwable) {
      null
    }

    KafkaRegistryUtil.parseFields(schema)
  }

  companion object {
    fun getInstance(connectionId: String,
                    project: Project) = (DriverManager.getDriverById(project, connectionId) as? KafkaDriver)?.dataManager
  }
}