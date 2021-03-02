package com.jetbrains.bigdatatools.kafka.data

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.jetbrains.bigdatatools.connection.updater.IntervalUpdateSettings
import com.jetbrains.bigdatatools.kafka.client.KafkaClient
import com.jetbrains.bigdatatools.kafka.model.ConsumerGroupPresentable
import com.jetbrains.bigdatatools.kafka.model.TopicPresentable
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.monitoring.data.MonitoringDataManager
import com.jetbrains.bigdatatools.monitoring.data.model.ObjectDataModel
import com.jetbrains.bigdatatools.rfs.driver.manager.DriverManager

class KafkaDataManager(project: Project?,
                       connectionData: KafkaConnectionData,
                       settings: IntervalUpdateSettings) : MonitoringDataManager(project, settings) {
  override val client = KafkaClient(project, connectionData)

  val topicModel = createTopicsDataModel()
  val consumerGroupsModel = createConsumerGroupsDataModel()

  init {
    Disposer.register(this, client)
    Disposer.register(this, topicModel)
    Disposer.register(this, consumerGroupsModel)
  }

  override fun dispose() {}

  override fun disposeInvalidatedData() {}


  private fun createTopicsDataModel(): ObjectDataModel<TopicPresentable> {
    val topicDataModel = object : ObjectDataModel<TopicPresentable>(TopicPresentable::class) {}

    addDataModelUpdater(topicDataModel, "Cannot request topic model") {
      val topics = client.getTopics(KafkaToolWindowSettings.getInstance().showInternalTopics)
      topicDataModel.setData(topics)
    }

    return topicDataModel
  }

  private fun createConsumerGroupsDataModel(): ObjectDataModel<ConsumerGroupPresentable> {
    val topicDataModel = object : ObjectDataModel<ConsumerGroupPresentable>(ConsumerGroupPresentable::class) {}

    addDataModelUpdater(topicDataModel, "Cannot request topic model") {
      val data = client.getConsumerGroups()
      topicDataModel.setData(data)
    }

    return topicDataModel
  }


  companion object {
    fun getInstance(connectionId: String, project: Project): KafkaDataManager? =
      (DriverManager.getDriverById(project, connectionId) as? KafkaDriver)?.dataManager
  }
}