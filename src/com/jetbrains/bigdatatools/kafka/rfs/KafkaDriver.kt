package com.jetbrains.bigdatatools.kafka.rfs

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.toolwindow.KafkaMonitoringToolWindowController
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.monitoring.rfs.MonitoringDriver
import icons.BigdatatoolsKafkaIcons
import javax.swing.Icon

class KafkaDriver(override val connectionData: KafkaConnectionData, project: Project?) : MonitoringDriver(project) {
  override val dataManager: KafkaDataManager = KafkaDataManager(project, connectionData,
                                                                KafkaToolWindowSettings.getInstance())
  override val presentableName: String = connectionData.name
  override val icon: Icon = BigdatatoolsKafkaIcons.Kafka

  init {
    Disposer.register(this, dataManager)
  }

  override fun dispose() {}

  override fun getController(project: Project) = KafkaMonitoringToolWindowController.getInstance(project)
}