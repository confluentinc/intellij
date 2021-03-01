package com.jetbrains.bigdatatools.kafka.data

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.jetbrains.bigdatatools.connection.updater.IntervalUpdateSettings
import com.jetbrains.bigdatatools.kafka.client.KafkaClient
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver
import com.jetbrains.bigdatatools.monitoring.data.MonitoringDataManager
import com.jetbrains.bigdatatools.rfs.driver.DriverConnectionStatus
import com.jetbrains.bigdatatools.rfs.driver.manager.DriverManager

class KafkaDataManager(project: Project?,
                       private val connectionData: KafkaConnectionData,
                       settings: IntervalUpdateSettings) : MonitoringDataManager(project, settings) {
  private val client = KafkaClient(connectionData)

  init {
    Disposer.register(this, client)
  }

  override fun dispose() {}

  override fun connect() = checkConnection()

  override fun checkConnection(): DriverConnectionStatus {
    val error = client.checkConnection()
    return error?.let { DriverConnectionStatus.FAILED } ?: DriverConnectionStatus.CONNECTED
  }

  override fun getRealUrl(): String = connectionData.uri

  fun getTopicsList() = client.getTopics(true)

  companion object {
    fun getInstance(connectionId: String, project: Project): KafkaDataManager? =
      (DriverManager.getDriverById(project, connectionId) as? KafkaDriver)?.dataManager
  }

}