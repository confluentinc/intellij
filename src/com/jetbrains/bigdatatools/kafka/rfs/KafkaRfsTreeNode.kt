package com.jetbrains.bigdatatools.kafka.rfs

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.jetbrains.bigdatatools.common.monitoring.rfs.MonitoringRfsTreeNode
import com.jetbrains.bigdatatools.common.rfs.driver.RfsPath
import com.jetbrains.bigdatatools.kafka.model.TopicPresentable
import com.jetbrains.bigdatatools.kafka.registry.common.KafkaSchemaInfo
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver.Companion.isSchemas
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver.Companion.isTopicFolder
import com.jetbrains.bigdatatools.kafka.toolwindow.KafkaMonitoringToolWindowController

class KafkaRfsTreeNode(
  project: Project,
  rfsPath: RfsPath,
  private val topic: TopicPresentable?,
  private val schema: KafkaSchemaInfo?,
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

  override fun getGrayText(): String? {
    val kafkaFileInfo = fileInfo as? KafkaFileInfo ?: return null
    return when {
      rfsPath.parent?.isSchemas == true -> {
        kafkaFileInfo.driver.dataManager.getCachedSchema(rfsPath.name)?.type?.presentable
      }
      else -> null
    }
  }


  override fun getIdleIcon() = when {
    rfsPath.isRoot -> super.getIdleIcon()
    rfsPath.parent?.isTopicFolder == true && topic?.isFavorite == true -> AllIcons.Nodes.Favorite
    rfsPath.parent?.isSchemas == true && schema?.isFavorite == true -> AllIcons.Nodes.Favorite
    else -> null
  }
}