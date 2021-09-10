package com.jetbrains.bigdatatools.kafka.data

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.jetbrains.bigdatatools.connection.updater.IntervalUpdateSettings
import com.jetbrains.bigdatatools.kafka.client.KafkaClient
import com.jetbrains.bigdatatools.kafka.model.ConsumerGroupPresentable
import com.jetbrains.bigdatatools.kafka.model.TopicConfig
import com.jetbrains.bigdatatools.kafka.model.TopicPartition
import com.jetbrains.bigdatatools.kafka.model.TopicPresentable
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.monitoring.data.MonitoringDataManager
import com.jetbrains.bigdatatools.monitoring.data.model.ObjectDataModel
import com.jetbrains.bigdatatools.monitoring.data.model.ProjectionObjectDataModel
import com.jetbrains.bigdatatools.monitoring.data.model.RemoteInfo
import com.jetbrains.bigdatatools.rfs.driver.manager.DriverManager

class KafkaDataManager(project: Project?,
                       connectionData: KafkaConnectionData,
                       settings: IntervalUpdateSettings) : MonitoringDataManager(project, settings) {
  val connectionId = connectionData.innerId
  override val client = KafkaClient(project, connectionData, false)

  val topicModel = createTopicsDataModel()
  val consumerGroupsModel = createConsumerGroupsDataModel()

  var topicConfigsModels = mapOf<String, ProjectionObjectDataModel<TopicConfig, TopicPresentable>>()
    private set
  private var topicPartitionsModels = mapOf<String, ProjectionObjectDataModel<TopicPartition, TopicPresentable>>()

  init {
    Disposer.register(this, client)
    Disposer.register(this, topicModel)
    Disposer.register(this, consumerGroupsModel)
  }

  override fun dispose() {}

  override fun disposeInvalidatedData() {
    topicConfigsModels = topicConfigsModels - disposeInvalidatedValues(topicConfigsModels).keys
    topicPartitionsModels = topicPartitionsModels - disposeInvalidatedValues(topicPartitionsModels).keys
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

      if (showFullTopicConfig)
        topic.topicConfigs
      else
        topic.topicConfigs.filter { it.value != it.defaultValue }
    }

    topicConfigsModels = topicConfigsModels + (topicName to dataModel)

    return dataModel
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


  private inline fun <reified T : RemoteInfo> getTopicProjectorModel(
    idField: String,
    noinline dataTransform: (List<TopicPresentable>) -> List<T>): ProjectionObjectDataModel<T, TopicPresentable> {

    val dataModel = ProjectionObjectDataModel(T::class, idField, topicModel, dataTransform)
    Disposer.register(this, dataModel)

    return dataModel
  }

  companion object {
    fun getInstance(connectionId: String, project: Project): KafkaDataManager? =
      (DriverManager.getDriverById(project, connectionId) as? KafkaDriver)?.dataManager
  }
}