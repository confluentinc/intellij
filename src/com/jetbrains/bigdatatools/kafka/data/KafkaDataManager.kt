package com.jetbrains.bigdatatools.kafka.data

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.jetbrains.bigdatatools.common.connection.updater.IntervalUpdateSettings
import com.jetbrains.bigdatatools.common.monitoring.data.MonitoringDataManager
import com.jetbrains.bigdatatools.common.monitoring.data.model.ObjectDataModel
import com.jetbrains.bigdatatools.common.monitoring.data.storage.ObjectDataModelStorage
import com.jetbrains.bigdatatools.common.monitoring.data.storage.RootDataModelStorage
import com.jetbrains.bigdatatools.common.rfs.driver.manager.DriverManager
import com.jetbrains.bigdatatools.common.rfs.util.RfsNotificationUtils
import com.jetbrains.bigdatatools.common.util.executeOnPooledThread
import com.jetbrains.bigdatatools.kafka.client.KafkaClient
import com.jetbrains.bigdatatools.kafka.common.models.RegistrySchemaInEditor
import com.jetbrains.bigdatatools.kafka.consumer.editor.KafkaConsumerPanelStorage
import com.jetbrains.bigdatatools.kafka.model.ConsumerGroupPresentable
import com.jetbrains.bigdatatools.kafka.model.TopicConfig
import com.jetbrains.bigdatatools.kafka.model.TopicPartition
import com.jetbrains.bigdatatools.kafka.model.TopicPresentable
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryType
import com.jetbrains.bigdatatools.kafka.registry.confluent.ConfluentRegistryDataManager
import com.jetbrains.bigdatatools.kafka.registry.glue.GlueRegistryDataManager
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver
import com.jetbrains.bigdatatools.kafka.statistics.KafkaUsagesCollector
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

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

  val isConfluentSchemaRegistryEnabled = connectionData.registryType == KafkaRegistryType.CONFLUENT
  val isGlueSchemaRegistryEnabled = connectionData.registryType == KafkaRegistryType.AWS_GLUE

  val confluentSchemaRegistry = if (connectionData.registryType == KafkaRegistryType.CONFLUENT)
    ConfluentRegistryDataManager(this) { client.confluentRegistryClient }
  else
    null

  val glueSchemaRegistry = if (connectionData.registryType == KafkaRegistryType.AWS_GLUE)
    GlueRegistryDataManager(this) { client.glueRegistryClient }
  else
    null


  init {
    confluentSchemaRegistry?.let { Disposer.register(this, it) }
    glueSchemaRegistry?.let { Disposer.register(this, it) }

    init()
    RootDataModelStorage(updater, listOfNotNull(topicModel, consumerGroupsModel,
                                                confluentSchemaRegistry?.schemaRegistryModel,
                                                glueSchemaRegistry?.schemaModel)).also { Disposer.register(this, it) }
  }

  fun getTopics() = topicModel.data ?: emptyList()

  fun createTopic(name: String, numPartition: Int?, replicaFactor: Int?) = actionWrapper {
    client.createTopic(name, numPartition, replicaFactor)
    updater.invokeRefreshModel(topicModel)

    KafkaUsagesCollector.topicCreatedEvent.log(project)
  }

  fun initRefreshSchemasIfRequired() {
    val confluentSchemaModel = confluentSchemaRegistry?.schemaRegistryModel
    if (confluentSchemaModel?.isInitedByFirstTime == false) {
      updater.invokeRefreshModel(confluentSchemaModel)
    }
    val glueSchema = glueSchemaRegistry?.schemaModel
    if (glueSchema?.isInitedByFirstTime == false) {
      updater.invokeRefreshModel(glueSchema)
    }
  }

  fun getSchemasForEditor(): List<RegistrySchemaInEditor> {
    val confluentSchemaModel = confluentSchemaRegistry?.schemaRegistryModel
    val glueSchema = glueSchemaRegistry?.schemaModel

    val confluentSchemas = confluentSchemaModel?.data?.map {
      RegistrySchemaInEditor(schemaName = it.name, registryName = "")
    }?.sorted()
    val glueSchemas = glueSchema?.data?.map {
      RegistrySchemaInEditor(schemaName = it.schemaName, registryName = it.registryName)
    }?.sorted()

    val schemas = confluentSchemas ?: glueSchemas
    return schemas ?: emptyList()
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


  private fun actionWrapper(body: () -> Unit) = executeOnPooledThread {
    try {
      body()
    }
    catch (t: Throwable) {
      RfsNotificationUtils.showExceptionMessage(project, t)
    }
  }


  private fun createTopicPartitionsStorage() = ObjectDataModelStorage<String, TopicPartition>(updater,
                                                                                              TopicPartition::partitionId,
                                                                                              dependOn = topicModel) { topicName ->
    val topics = topicModel.data ?: emptyList()
    val topic = topics.find { it.name == topicName } ?: error(KafkaMessagesBundle.message("topic.not.found", topicName))
    topic.partitionList
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

  companion object {
    fun getInstance(connectionId: String,
                    project: Project) = (DriverManager.getDriverById(project, connectionId) as? KafkaDriver)?.dataManager
  }
}