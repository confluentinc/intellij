package com.jetbrains.bigdatatools.kafka.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.jetbrains.bigdatatools.connection.updater.IntervalUpdateSettings
import com.jetbrains.bigdatatools.kafka.client.KafkaClient
import com.jetbrains.bigdatatools.kafka.model.ConsumerGroupPresentable
import com.jetbrains.bigdatatools.kafka.model.TopicConfigPresentable
import com.jetbrains.bigdatatools.kafka.model.TopicPartition
import com.jetbrains.bigdatatools.kafka.model.TopicPresentable
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.monitoring.data.MonitoringDataManager
import com.jetbrains.bigdatatools.monitoring.data.listener.DataModelListener
import com.jetbrains.bigdatatools.monitoring.data.model.ObjectDataModel
import com.jetbrains.bigdatatools.monitoring.data.model.RemoteInfo
import com.jetbrains.bigdatatools.rfs.driver.manager.DriverManager

class KafkaDataManager(project: Project?,
                       connectionData: KafkaConnectionData,
                       settings: IntervalUpdateSettings) : MonitoringDataManager(project, settings) {
  val connectionId = connectionData.innerId
  override val client = KafkaClient(project, connectionData, false)

  val topicModel = createTopicsDataModel()
  val consumerGroupsModel = createConsumerGroupsDataModel()

  private var topicConfigsModels = mapOf<String, ObjectDataModel<TopicConfigPresentable>>()
  private var topicPartitionsModels = mapOf<String, ObjectDataModel<TopicPartition>>()

  init {
    Disposer.register(this, client)
    Disposer.register(this, topicModel)
    Disposer.register(this, consumerGroupsModel)
  }

  override fun dispose() {}

  override fun disposeInvalidatedData() {}

  @Suppress("DuplicatedCode")
  fun getTopicPartitionsModel(topicName: String): ObjectDataModel<TopicPartition> {
    topicPartitionsModels[topicName]?.let {
      return it
    }

    val dataModel = getTopicProjectorModel {
      val topic = topicModel.entries.find { it.name == topicName } ?: error(KafkaMessagesBundle.message("topic.not.found", topicName))
      topic.partitions
    }

    topicPartitionsModels = topicPartitionsModels + (topicName to dataModel)

    return dataModel
  }


  @Suppress("DuplicatedCode")
  fun getTopicConfigsModel(topicName: String): ObjectDataModel<TopicConfigPresentable> {
    topicConfigsModels[topicName]?.let {
      return it
    }

    val dataModel = getTopicProjectorModel {
      val topic = topicModel.entries.find { it.name == topicName } ?: error(KafkaMessagesBundle.message("topic.not.found", topicName))
      topic.topicConfigs
    }

    topicConfigsModels = topicConfigsModels + (topicName to dataModel)

    return dataModel
  }

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


  private inline fun <reified T : RemoteInfo> getTopicProjectorModel(crossinline getData: () -> List<T>): ObjectDataModel<T> {
    val dataModel = object : ObjectDataModel<T>(T::class) {
      init {
        updateData()
      }

      fun updateData() {
        val newData = try {
          getData()
        }
        catch (t: Throwable) {
          setError("Update Error", t)
          return
        }
        if (newData != data)
          setData(newData)
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

    return dataModel
  }

  companion object {
    fun getInstance(connectionId: String, project: Project): KafkaDataManager? =
      (DriverManager.getDriverById(project, connectionId) as? KafkaDriver)?.dataManager
  }
}