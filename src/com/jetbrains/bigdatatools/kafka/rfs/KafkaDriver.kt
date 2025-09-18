package com.jetbrains.bigdatatools.kafka.rfs

import com.intellij.bigdatatools.kafka.icons.BigdatatoolsKafkaIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.jetbrains.bigdatatools.kafka.core.monitoring.data.listener.DataModelListener
import com.jetbrains.bigdatatools.kafka.core.monitoring.rfs.MonitoringDriver
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.Driver
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.FileInfo
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.RfsPath
import com.jetbrains.bigdatatools.kafka.core.rfs.tree.DriverRfsTreeModel
import com.jetbrains.bigdatatools.kafka.core.rfs.tree.node.RfsDriverTreeNodeBuilder
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryType
import com.jetbrains.bigdatatools.kafka.toolwindow.KafkaMonitoringToolWindowController
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.kafka.toolwindow.controllers.KafkaGroupType
import javax.swing.Icon

class KafkaDriver(override val connectionData: KafkaConnectionData, project: Project?, testConnection: Boolean) : MonitoringDriver(project,
                                                                                                                                   testConnection) {
  override val dataManager: KafkaDataManager = KafkaDataManager(project, connectionData,
                                                                KafkaToolWindowSettings.getInstance())
  override val presentableName: String = connectionData.name
  override val icon: Icon = BigdatatoolsKafkaIcons.Kafka

  override val treeNodeBuilder: RfsDriverTreeNodeBuilder = object : RfsDriverTreeNodeBuilder() {
    override fun createNode(project: Project, path: RfsPath, driver: Driver) =
      KafkaRfsTreeNode(project, path, dataManager.getCachedTopicByName(path.name), dataManager.getSchemaByName(path.name),
                       dataManager.getCachedConsumerGroup(path.name), this@KafkaDriver)
  }

  init {
    Disposer.register(this, dataManager)

    dataManager.topicModel.addListener(object : DataModelListener {
      override fun onChanged() {
        fileInfoManager.refreshFiles(topicPath)
      }
    })
    dataManager.consumerGroupsModel.addListener(object : DataModelListener {
      override fun onChanged() {
        fileInfoManager.refreshFiles(consumerPath)
      }
    })

    dataManager.schemaRegistryModel?.addListener(object : DataModelListener {
      override fun onChanged() {
        fileInfoManager.refreshFiles(schemasPath)
      }
    })
  }

  override fun dispose() {}

  override fun createTreeModel(rootPath: RfsPath, project: Project) = DriverRfsTreeModel(project, rootPath, this, false)

  override fun doLoadFileInfo(rfsPath: RfsPath) = KafkaFileInfo(this, rfsPath)

  override fun doLoadChildren(rfsPath: RfsPath): List<FileInfo>? {
    dataManager.client.connectionError?.let {
      throw it
    }
    val children = when {
      rfsPath.isRoot -> listOfNotNull(topicPath,
                                      if (dataManager.registryType != KafkaRegistryType.NONE) schemasPath else null,
                                      consumerPath)
      rfsPath.isTopicFolder -> {
        dataManager.topicModel.error?.let { throw it }
        dataManager.topicModel.data?.map { topicPath.child(it.name, false) } ?: emptyList()
      }
      rfsPath.isConsumers -> {
        dataManager.consumerGroupsModel.error?.let { throw it }
        dataManager.consumerGroupsModel.data?.map { consumerPath.child(it.consumerGroup, false) } ?: emptyList()
      }
      rfsPath.isSchemas -> {
        dataManager.schemaRegistryModel?.error?.let { throw it }
        dataManager.schemaRegistryModel?.data?.map { schemasPath.child(it.name, false) } ?: emptyList()
      }
      else -> null
    }
    return children?.map { KafkaFileInfo(this, it) }
  }

  override fun getController(project: Project) = KafkaMonitoringToolWindowController.getInstance(project)

  companion object {
    val topicPath = RfsPath(listOf(KafkaGroupType.TOPIC.title), true)
    val consumerPath = RfsPath(listOf(KafkaGroupType.CONSUMER_GROUP.title), true)
    val schemasPath = RfsPath(listOf(KafkaGroupType.SCHEMA_REGISTRY_GROUP.title), true)

    val RfsPath.isTopicFolder
      get() = this == topicPath
    val RfsPath.isConsumers
      get() = this == consumerPath
    val RfsPath.isSchemas
      get() = this == schemasPath
  }
}