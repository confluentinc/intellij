package com.jetbrains.bigdatatools.kafka.rfs

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.jetbrains.bigdatatools.kafka.util.KafkaIcons
import com.jetbrains.bigdatatools.kafka.manager.KafkaMonitoringDataManager
import com.jetbrains.bigdatatools.kafka.toolwindow.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.monitoring.rfs.MonitoringDriver
import com.jetbrains.bigdatatools.monitoring.toolwindow.MonitoringToolWindowController
import javax.swing.Icon

class KafkaDriver(override val connectionData: KafkaConnectionData, project: Project?) : MonitoringDriver(project) {
  override val dataManager: KafkaMonitoringDataManager = KafkaMonitoringDataManager(project, connectionData,
                                                                                    KafkaToolWindowSettings.getInstance())
  override val presentableName: String = connectionData.name
  override val icon: Icon = KafkaIcons.MAIN_ICON

  init {
    Disposer.register(this, dataManager)
  }

  override fun dispose() {}

  override fun getController(project: Project): MonitoringToolWindowController {
    TODO("Not yet implemented")
  }
}