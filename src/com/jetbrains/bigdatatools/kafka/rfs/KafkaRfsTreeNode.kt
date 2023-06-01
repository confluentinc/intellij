package com.jetbrains.bigdatatools.kafka.rfs

import com.intellij.openapi.project.Project
import com.jetbrains.bigdatatools.core.monitoring.rfs.MonitoringRfsTreeNode
import com.jetbrains.bigdatatools.core.rfs.driver.RfsPath
import com.jetbrains.bigdatatools.kafka.toolwindow.KafkaMonitoringToolWindowController

class KafkaRfsTreeNode(
  project: Project,
  rfsPath: RfsPath,
  driver: KafkaDriver,
) : MonitoringRfsTreeNode(project, rfsPath, driver) {
  init {
    myName = rfsPath.name
  }

  override fun isAlwaysLeaf() = rfsPath.isFile

  override fun onDoubleClick(): Boolean {
    val project = project ?: return true
    val controller = driver.getController(project) as? KafkaMonitoringToolWindowController
    controller?.focusOn(focusId, rfsPath)
    return true
  }

  override fun getIdleIcon() = if (rfsPath.isRoot)
    super.getIdleIcon()
  else
    null
}