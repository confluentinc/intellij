package com.jetbrains.bigdatatools.kafka.data

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.jetbrains.bigdatatools.common.connection.updater.IntervalUpdateSettings
import com.jetbrains.bigdatatools.common.monitoring.data.MonitoringDataManager
import com.jetbrains.bigdatatools.common.monitoring.data.model.ObjectDataModel
import com.jetbrains.bigdatatools.common.monitoring.data.model.ProjectionObjectDataModel
import com.jetbrains.bigdatatools.common.monitoring.data.model.RemoteInfo
import com.jetbrains.bigdatatools.common.rfs.driver.manager.DriverManager
import com.jetbrains.bigdatatools.common.rfs.util.RfsNotificationUtils
import com.jetbrains.bigdatatools.common.util.executeOnPooledThread
import com.jetbrains.bigdatatools.common.util.withCatchNotifyError
import com.jetbrains.bigdatatools.kafka.client.KafkaClient
import com.jetbrains.bigdatatools.kafka.consumer.editor.KafkaConsumerPanelStorage
import com.jetbrains.bigdatatools.kafka.model.*
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryUtil
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryUtil.registrySchemaProviders
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver
import com.jetbrains.bigdatatools.kafka.statistics.KafkaUsagesCollector
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient

class KafkaDataManager(project: Project?,
                       val connectionData: KafkaConnectionData,
                       settings: IntervalUpdateSettings) : MonitoringDataManager(project, settings) {
  val connectionId = connectionData.innerId
  override val client = KafkaClient(project, connectionData, false)

  val isKafkaRegistryEnabled = connectionData.registryUrl?.isNotBlank() == true

  private val registryClient = if (isKafkaRegistryEnabled)
    CachedSchemaRegistryClient(listOf(connectionData.registryUrl!!), 100, registrySchemaProviders, null)
  else
    null

  internal val topicModel = createTopicsDataModel()
  internal val registrySchemaModel = createSchemaRegistryDataModel()
  internal val consumerGroupsModel = createConsumerGroupsDataModel()

  var topicConfigsModels = mapOf<String, ProjectionObjectDataModel<TopicConfig, TopicPresentable>>()
    private set

  private var topicPartitionsModels = mapOf<String, ProjectionObjectDataModel<TopicPartition, TopicPresentable>>()

  private var schemaFieldsModels = mapOf<Int, ObjectDataModel<SchemaRegistryFieldsInfo>>()
  private var schemaVersionsModels = mapOf<Int, ObjectDataModel<SchemaRegistryInfo>>()

  val consumerPanelStorage = KafkaConsumerPanelStorage(this)

  init {
    Disposer.register(this, consumerPanelStorage)
    Disposer.register(this, client)
    Disposer.register(this, topicModel)
    Disposer.register(this, consumerGroupsModel)

    registrySchemaModel?.let { Disposer.register(this, it) }

    init()
  }

  override fun dispose() {}

  override fun disposeInvalidatedData() {
    topicConfigsModels = topicConfigsModels - disposeInvalidatedValues(topicConfigsModels).keys
    topicPartitionsModels = topicPartitionsModels - disposeInvalidatedValues(topicPartitionsModels).keys
    schemaFieldsModels = schemaFieldsModels - disposeInvalidatedValues(schemaFieldsModels).keys
  }

  @Suppress("DuplicatedCode")
  fun getTopicPartitionsModel(topicName: String): ObjectDataModel<TopicPartition> {
    topicPartitionsModels[topicName]?.let {
      return it
    }

    val dataModel = getTopicProjectorModel(TopicPartition::partitionId.name) { topics ->
      val topic = topics.find { it.name == topicName } ?: error(KafkaMessagesBundle.message("topic.not.found", topicName))
      topic.partitionList
    }

    topicPartitionsModels = topicPartitionsModels + (topicName to dataModel)

    return dataModel
  }

  fun getTopics() = topicModel.data ?: emptyList()

  @Suppress("DuplicatedCode")
  fun getTopicConfigsModel(topicName: String): ProjectionObjectDataModel<TopicConfig, TopicPresentable> {
    topicConfigsModels[topicName]?.let {
      return it
    }

    val dataModel = getTopicProjectorModel(TopicConfig::name.name) { topics ->
      val topic = topics.find { it.name == topicName } ?: error(KafkaMessagesBundle.message("topic.not.found", topicName))
      val showFullTopicConfig = KafkaToolWindowSettings.getInstance().showFullTopicConfig

      if (showFullTopicConfig) topic.topicConfigs
      else topic.topicConfigs.filter { it.value != it.defaultValue }
    }

    topicConfigsModels = topicConfigsModels + (topicName to dataModel)

    return dataModel
  }

  fun createTopic(name: String, numPartition: Int?, replicaFactor: Int?) = actionWrapper {
    client.createTopic(name, numPartition, replicaFactor)
    autoUpdaterManager.reloadAsync(topicModel)

    KafkaUsagesCollector.topicCreatedEvent.log(project)
  }

  fun deleteTopic(topicNames: List<String>) = actionWrapper {
    topicNames.forEach {
      client.deleteTopic(it)
    }

    autoUpdaterManager.reloadAsync(topicModel)

    KafkaUsagesCollector.topicDeletedEvent.log(project)
  }

  private fun createTopicsDataModel(): ObjectDataModel<TopicPresentable> {
    val topicDataModel = object : ObjectDataModel<TopicPresentable>(TopicPresentable::class) {
      override val idFieldName: String = "name"
    }

    addDataModelUpdater(topicDataModel, "Cannot request topic model") {
      val topics = client.getTopics(KafkaToolWindowSettings.getInstance().showInternalTopics)
      topicDataModel.setData(topics)
    }

    return topicDataModel
  }

  private fun createConsumerGroupsDataModel(): ObjectDataModel<ConsumerGroupPresentable> {
    val topicDataModel = object : ObjectDataModel<ConsumerGroupPresentable>(ConsumerGroupPresentable::class) {
      override val idFieldName: String = "consumerGroup"
    }

    addDataModelUpdater(topicDataModel, "Cannot request consumer groups") {
      val data = client.getConsumerGroups()
      topicDataModel.setData(data)
    }

    return topicDataModel
  }

  private fun createSchemaRegistryDataModel(): ObjectDataModel<SchemaRegistryInfo>? {
    val client = registryClient ?: return null
    val dataModel = object : ObjectDataModel<SchemaRegistryInfo>(SchemaRegistryInfo::class) {
      override val idFieldName: String = SchemaRegistryInfo::name.name
    }

    addDataModelUpdater(dataModel, "Cannot request topic model") {
      val subjects = client.getAllSubjects(KafkaToolWindowSettings.getInstance().registryShowDeletedSubjects)

      val infos = subjects.map {
        val meta = try {
          client.getLatestSchemaMetadata(it)
        }
        catch (t: Throwable) {
          null
        }
        SchemaRegistryInfo(name = it, meta = meta)
      }

      dataModel.setData(infos)
    }

    return dataModel
  }

  fun getRegistrySchemaFieldsModel(id: Int): ObjectDataModel<SchemaRegistryFieldsInfo> {
    schemaFieldsModels[id]?.let {
      return it
    }

    val client = registryClient!!

    val dataModel = ObjectDataModel(SchemaRegistryFieldsInfo::class)

    addDataModelUpdater(dataModel, "Cannot request environment model") {
      val meta = getSchemaInfo(id)?.meta ?: error("Meta is not found")

      val schema = try {
        client.parseSchema(meta.schemaType, meta.schema, meta.references).get()
      }
      catch (t: Throwable) {
        null
      }

      val fields = KafkaRegistryUtil.parseFields(schema)
      dataModel.setData(fields)
    }

    schemaFieldsModels = schemaFieldsModels + (id to dataModel)

    return dataModel
  }


  fun getRegistrySchemaVersionsModel(id: Int): ObjectDataModel<SchemaRegistryInfo> {
    schemaVersionsModels[id]?.let {
      return it
    }

    val client = registryClient!!

    val dataModel = ObjectDataModel(SchemaRegistryInfo::class)

    addDataModelUpdater(dataModel, "Cannot request environment model") {
      val meta = getSchemaInfo(id) ?: error("Meta is not found")

      val versions = client.getAllVersions(meta.name) ?: emptyList()
      val metas = versions.map {
        val versionMeta = client.getSchemaMetadata(meta.name, it)
        SchemaRegistryInfo(name = meta.name, meta = versionMeta)
      }
      dataModel.setData(metas)
    }

    schemaVersionsModels = schemaVersionsModels + (id to dataModel)

    return dataModel
  }

  fun getSchemaInfo(id: Int) = registrySchemaModel?.data?.firstOrNull { it.id == id }


  private inline fun <reified T : RemoteInfo> getTopicProjectorModel(idField: String,
                                                                     noinline dataTransform: (List<TopicPresentable>) -> List<T>): ProjectionObjectDataModel<T, TopicPresentable> {

    val dataModel = ProjectionObjectDataModel(T::class, idField, topicModel, dataTransform)
    Disposer.register(this, dataModel)

    return dataModel
  }

  private fun actionWrapper(body: () -> Unit) = executeOnPooledThread {
    try {
      body()
    }
    catch (t: Throwable) {
      RfsNotificationUtils.showExceptionMessage(project, t)
    }
  }

  fun deleteRegistrySchemaVersion(registryInfo: SchemaRegistryInfo, isPermanent: Boolean = false) = executeOnPooledThread {
    withCatchNotifyError {
      val name = registryInfo.name
      registryClient?.deleteSchemaVersion(name, registryInfo.version.toString(), isPermanent)

      registrySchemaModel?.let {
        autoUpdaterManager.reloadAsync(it)
      }
      autoUpdaterManager.reloadAsync(getRegistrySchemaVersionsModel(registryInfo.id))
    }
  }


  fun deleteRegistrySchema(registryInfo: SchemaRegistryInfo) = executeOnPooledThread {
    withCatchNotifyError {
      val name = registryInfo.name
      registryClient?.deleteSubject(name)
      registrySchemaModel?.let { autoUpdaterManager.reloadAsync(it) }
    }
  }

  fun createRegistrySubject(schemaName: String, parsedSchema: ParsedSchema) = executeOnPooledThread {
    withCatchNotifyError {
      registryClient?.register(schemaName, parsedSchema)
      registrySchemaModel?.let { autoUpdaterManager.reloadAsync(it) }
    }
  }

  fun updateSchema(registryInfo: SchemaRegistryInfo,
                   newText: @NlsSafe String) = executeOnPooledThread {
    withCatchNotifyError {
      val registryClient = registryClient ?: return@withCatchNotifyError
      val parsedSchema = KafkaRegistryUtil.validateSchema(registryInfo, newText)
      registryClient.register(registryInfo.name, parsedSchema)

      registrySchemaModel?.let {
        autoUpdaterManager.reloadAsync(it)
      }
      autoUpdaterManager.reloadAsync(getRegistrySchemaVersionsModel(registryInfo.id))
    }
  }

  fun getRegistrySchema(subjectName: String) = registrySchemaModel?.data?.firstOrNull { it.name == subjectName }

  fun getRegistrySchemaById(id: Int?): ParsedSchema? {
    id ?: return null
    return registryClient?.getSchemaById(id)
  }

  companion object {
    fun getInstance(connectionId: String,
                    project: Project): KafkaDataManager? = (DriverManager.getDriverById(project, connectionId) as? KafkaDriver)?.dataManager
  }
}