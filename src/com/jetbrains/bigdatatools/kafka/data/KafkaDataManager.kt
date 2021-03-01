package com.jetbrains.bigdatatools.kafka.data

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.jetbrains.bigdatatools.connection.updater.IntervalUpdateSettings
import com.jetbrains.bigdatatools.kafka.client.KafkaClient
import com.jetbrains.bigdatatools.kafka.model.TopicPresentable
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver
import com.jetbrains.bigdatatools.monitoring.data.MonitoringDataManager
import com.jetbrains.bigdatatools.monitoring.data.model.ObjectDataModel
import com.jetbrains.bigdatatools.rfs.driver.DriverConnectionStatus
import com.jetbrains.bigdatatools.rfs.driver.manager.DriverManager

class KafkaDataManager(project: Project?,
                       private val connectionData: KafkaConnectionData,
                       settings: IntervalUpdateSettings) : MonitoringDataManager(project, settings) {
  private val client = KafkaClient(connectionData)

  private val topicModel = object : ObjectDataModel<TopicPresentable>(TopicPresentable::class) {}

  init {
    topicModel.setData(client.getTopics(true))

    Disposer.register(this, client)
    Disposer.register(this, topicModel)
  }

  override fun dispose() {}

  override fun connect() = checkConnection()

  override fun checkConnection(): DriverConnectionStatus {
    val error = client.checkConnection()
    return error?.let { DriverConnectionStatus.FAILED } ?: DriverConnectionStatus.CONNECTED
  }

  override fun getRealUrl(): String = connectionData.uri

  fun getTopicsList() = client.getTopics(true)
  fun getTopicModel() = topicModel

  companion object {
    fun getInstance(connectionId: String, project: Project): KafkaDataManager? =
      (DriverManager.getDriverById(project, connectionId) as? KafkaDriver)?.dataManager
  }

}