package com.jetbrains.bigdatatools.kafka.manager

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.jetbrains.bigdatatools.connection.updater.IntervalUpdateSettings
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.monitoring.data.MonitoringDataManager
import com.jetbrains.bigdatatools.rfs.driver.DriverConnectionStatus

class KafkaMonitoringDataManager(project: Project?,
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
}