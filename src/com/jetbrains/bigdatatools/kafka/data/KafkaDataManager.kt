package com.jetbrains.bigdatatools.kafka.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.jetbrains.bigdatatools.connection.updater.IntervalUpdateSettings
import com.jetbrains.bigdatatools.kafka.client.KafkaClient
import com.jetbrains.bigdatatools.kafka.model.ConsumerGroupPresentable
import com.jetbrains.bigdatatools.kafka.model.TopicConfigPresentable
import com.jetbrains.bigdatatools.kafka.model.TopicPresentable
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.monitoring.data.MonitoringDataManager
import com.jetbrains.bigdatatools.monitoring.data.listener.DataModelListener
import com.jetbrains.bigdatatools.monitoring.data.model.ObjectDataModel
import com.jetbrains.bigdatatools.rfs.driver.manager.DriverManager

class KafkaDataManager(project: Project?,
                       connectionData: KafkaConnectionData,
                       settings: IntervalUpdateSettings) : MonitoringDataManager(project, settings) {
  val connectionId = connectionData.innerId
  override val client = KafkaClient(project, connectionData, false)

  val topicModel = createTopicsDataModel()
  val consumerGroupsModel = createConsumerGroupsDataModel()

  private var topicConfigsModels = mapOf<String, ObjectDataModel<TopicConfigPresentable>>()

  init {
    Disposer.register(this, client)
    Disposer.register(this, topicModel)
    Disposer.register(this, consumerGroupsModel)
  }

  override fun dispose() {}

  override fun disposeInvalidatedData() {}


  private fun createTopicsDataModel(): ObjectDataModel<TopicPresentable> {
    val topicDataModel = ObjectDataModel(TopicPresentable::class)

    addDataModelUpdater(topicDataModel, "Cannot request topic model") {
      val topics = client.getTopics(KafkaToolWindowSettings.getInstance().showInternalTopics)
      topicDataModel.setData(topics)
    }

    return topicDataModel
  }

  private fun createConsumerGroupsDataModel(): ObjectDataModel<ConsumerGroupPresentable> {
    val topicDataModel = ObjectDataModel(ConsumerGroupPresentable::class)

    addDataModelUpdater(topicDataModel, "Cannot request topic model") {
      val data = client.getConsumerGroups()
      topicDataModel.setData(data)
    }

    return topicDataModel
  }

  fun getTopicConfigsModel(topicName: String): ObjectDataModel<TopicConfigPresentable> {
    topicConfigsModels[topicName]?.let {
      return it
    }

    val dataModel = object : ObjectDataModel<TopicConfigPresentable>(TopicConfigPresentable::class) {
      init {
        updateData()
      }

      fun updateData() {
        val topic = topicModel.entries.find { it.name == topicName } ?: let {
          setError(KafkaMessagesBundle.message("topic.not.found", topicName))
          return
        }
        setData(topic.topicConfigs)
      }

    }
    Disposer.register(this, dataModel)


    val topicModelListener = object : DataModelListener {
      override fun onChanged() = dataModel.updateData()
      override fun onError(msg: String, e: Throwable?) = dataModel.setError(msg, e)
    }

    topicModel.addListener(topicModelListener)
    Disposer.register(dataModel, Disposable {
      topicModel.removeListener(topicModelListener)
    })

    topicConfigsModels = topicConfigsModels + (topicName to dataModel)

    return dataModel
  }

  companion object {
    fun getInstance(connectionId: String, project: Project): KafkaDataManager? =
      (DriverManager.getDriverById(project, connectionId) as? KafkaDriver)?.dataManager
  }
}