package com.jetbrains.bigdatatools.kafka.toolwindow.controllers

import com.intellij.openapi.project.Project
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.TabbedDetailsMonitoringController
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

class KafkaRegistryTabController(project: Project,
                                 dataManager: KafkaDataManager) : TabbedDetailsMonitoringController(project) {
  private val fieldsController = KafkaRegistrySchemaFieldsController(project, dataManager)
  private val versionsController = KafkaRegistrySchemaVersionsController(project, dataManager)

  override val tabsControllers = listOf(
    KafkaMessagesBundle.message("registry.tab.fields") to fieldsController,
    KafkaMessagesBundle.message("registry.tab.versions") to versionsController
  )

  init {
    init()
  }
}