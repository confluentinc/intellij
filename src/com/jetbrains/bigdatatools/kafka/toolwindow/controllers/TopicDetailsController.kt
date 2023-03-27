package com.jetbrains.bigdatatools.kafka.toolwindow.controllers

import com.intellij.openapi.project.Project
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.DetailsMonitoringController
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.TabbedDetailsMonitoringController
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

class TopicDetailsController(project: Project, dataManager: KafkaDataManager) : TabbedDetailsMonitoringController<String>(project) {
  private val configsController = TopicConfigsController(project, dataManager)
  private val partitionsController = TopicPartitionsController(dataManager)

  override val tabsControllers: List<Pair<String, DetailsMonitoringController<String>>> = listOf(
    KafkaMessagesBundle.message("topic.tab.partitions") to partitionsController,
    KafkaMessagesBundle.message("topic.tab.configs") to configsController
  )

  init {
    init()
  }
}