package com.jetbrains.bigdatatools.kafka.toolwindow.controllers

import com.intellij.openapi.project.Project
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.monitoring.toolwindow.DetailsMonitoringController
import com.jetbrains.bigdatatools.monitoring.toolwindow.TabbedDetailsMonitoringController

class TopicDetailsController(project: Project, dataManager: KafkaDataManager) : TabbedDetailsMonitoringController(project) {
  private val configsController = TopicConfigsController(dataManager)
  private val partitionsController = TopicPartitionsController(dataManager)

  override val tabsControllers: List<Pair<String, DetailsMonitoringController>> = listOf(
    KafkaMessagesBundle.message("topic.tab.partitions") to partitionsController,
    KafkaMessagesBundle.message("topic.tab.configs") to configsController
  )

  init {
    init()
  }
}