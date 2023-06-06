package com.jetbrains.bigdatatools.kafka.toolwindow.controllers

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.PopupHandler
import com.intellij.util.ui.tree.TreeModelAdapter
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.MainTreeController
import com.jetbrains.bigdatatools.common.rfs.driver.RfsPath
import com.jetbrains.bigdatatools.common.rfs.projectview.actions.RfsActionPlaces
import com.jetbrains.bigdatatools.common.rfs.viewer.utils.DriverRfsTreeUtil.lastDriverNode
import com.jetbrains.bigdatatools.common.util.ToolbarUtils
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryType
import com.jetbrains.bigdatatools.kafka.registry.confluent.controller.KafkaRegistryController
import com.jetbrains.bigdatatools.kafka.registry.confluent.controller.KafkaSchemaController
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver.Companion.isConsumers
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver.Companion.isSchemas
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver.Companion.isTopicFolder
import com.jetbrains.bigdatatools.kafka.util.KafkaControllerUtils
import javax.swing.event.TreeModelEvent

/**
 * Main controller for Kafka Cluster.
 * Contains page control for Topics / ConsumerGroups / etc.
 */
class KafkaMainController(project: Project, connectionData: KafkaConnectionData)
  : MainTreeController<KafkaConnectionData, KafkaDriver>(project, connectionData) {
  override val dataManager: KafkaDataManager = driver.dataManager

  private val topicsController = TopicsController(project, dataManager, this).also { Disposer.register(this, it) }

  private val consumerGroupsController = ConsumerGroupsController(dataManager).also { Disposer.register(this, it) }

  private val registryController = if (dataManager.registryType != KafkaRegistryType.NONE)
    KafkaRegistryController(project, dataManager, this).also { Disposer.register(this, it) }
  else
    null

  private val topicInfoController = TopicDetailsController(project, dataManager).also { Disposer.register(this, it) }

  private val schemaInfoController = if (dataManager.registryType != KafkaRegistryType.NONE)
    KafkaSchemaController(project, dataManager).also { Disposer.register(this, it) }
  else
    null

  init {
    init()

    treeModel.addTreeModelListener(object : TreeModelAdapter() {
      override fun process(event: TreeModelEvent, type: EventType) {
        if (type != EventType.NodesRemoved && (myTree.selectionPath == null || myTree.selectionPath?.lastDriverNode?.rfsPath?.isRoot == true)) {
          myTree.selectionPath = treeModel.getTreePath(KafkaDriver.topicPath)
        }
      }
    })
  }

  override fun createToolbar() = ToolbarUtils.createActionToolbar("KafkaMainController", KafkaControllerUtils.createTopicToolbar(), false)

  override fun setupDriverSpecificTreeInit() {
    PopupHandler.installPopupMenu(myTree, "Kafka.Actions", RfsActionPlaces.RFS_PANE_POPUP)
    details.add(topicsController.getComponent(), KafkaGroupType.TOPIC.name)
    details.add(topicInfoController.getComponent(), KafkaGroupType.TOPIC_DETAIL.name)
    details.add(consumerGroupsController.getComponent(), KafkaGroupType.CONSUMER_GROUP.name)
    registryController?.let {
      details.add(it.getComponent(), KafkaGroupType.SCHEMA_REGISTRY_GROUP.name)
    }
    schemaInfoController?.let {
      details.add(it.getComponent(), KafkaGroupType.SCHEMA_DETAIL.name)
    }
  }

  override fun showDetailsComponent(rfsPath: RfsPath) {
    when {
      rfsPath.isRoot -> {
        showDetailsComponent(null)
      }
      rfsPath.isTopicFolder -> {
        showDetailsComponent(KafkaGroupType.TOPIC)
      }
      rfsPath.isConsumers || rfsPath.parent?.isConsumers == true -> {
        showDetailsComponent(KafkaGroupType.CONSUMER_GROUP)
      }
      rfsPath.isSchemas -> {
        showDetailsComponent(KafkaGroupType.SCHEMA_REGISTRY_GROUP)
      }
      rfsPath.parent?.isTopicFolder == true -> {
        showDetailsComponent(KafkaGroupType.TOPIC_DETAIL)
        topicInfoController.setDetailsId(rfsPath.name)
      }
      rfsPath.parent?.isSchemas == true -> {
        showDetailsComponent(KafkaGroupType.SCHEMA_DETAIL)
        schemaInfoController?.setDetailsId(rfsPath.name)
      }
    }
  }

  private fun showDetailsComponent(selectedValue: KafkaGroupType?) = detailsLayout.show(details, selectedValue?.name)
}