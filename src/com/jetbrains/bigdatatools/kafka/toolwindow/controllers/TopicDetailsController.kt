package com.jetbrains.bigdatatools.kafka.toolwindow.controllers

import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.DetailsMonitoringController
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.TabbedDetailsMonitoringController
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryType
import com.jetbrains.bigdatatools.kafka.registry.confluent.controller.KafkaTopicSchemaController
import com.jetbrains.bigdatatools.kafka.registry.confluent.controller.TopicSchemaViewType
import com.jetbrains.bigdatatools.kafka.rfs.KafkaDriver
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

class TopicDetailsController(project: Project, dataManager: KafkaDataManager) : TabbedDetailsMonitoringController<String>(project) {
  private val configsController = TopicConfigsController(project, dataManager).also { Disposer.register(this, it) }
  private val partitionsController = TopicPartitionsController(dataManager).also { Disposer.register(this, it) }

  override val tabsControllers: List<Pair<String, DetailsMonitoringController<String>>> = let {
    val origin = listOf(
      KafkaMessagesBundle.message("topic.tab.partitions") to partitionsController,
      KafkaMessagesBundle.message("topic.tab.configs") to configsController)

    val schemas = when (dataManager.connectionData.registryType) {
      KafkaRegistryType.NONE -> emptyList()
      KafkaRegistryType.CONFLUENT -> listOf(
        KafkaMessagesBundle.message("topic.tab.schema.key") to KafkaTopicSchemaController(project, dataManager, TopicSchemaViewType.KEY),
        KafkaMessagesBundle.message("topic.tab.schema.value") to KafkaTopicSchemaController(project, dataManager, TopicSchemaViewType.VALUE)
      )
      KafkaRegistryType.AWS_GLUE -> listOf(
        KafkaMessagesBundle.message("topic.tab.schema") to KafkaTopicSchemaController(project, dataManager, TopicSchemaViewType.TOPIC)
      )
    }
    schemas.forEach {
      Disposer.register(this, it.second)
    }
    origin + schemas
  }

  override val dataProvider = DataProvider { dataId ->
    when {
      KafkaMainController.DATA_MANAGER.`is`(dataId) -> dataManager
      KafkaMainController.RFS_PATH.`is`(dataId) -> detailsId?.let { KafkaDriver.topicPath.child(it, false) }
      else -> null
    }
  }

  init {
    init()
  }
}