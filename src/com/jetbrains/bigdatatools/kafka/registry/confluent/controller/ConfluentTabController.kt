package com.jetbrains.bigdatatools.kafka.registry.confluent.controller

import com.intellij.openapi.project.Project
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.TabbedDetailsMonitoringController
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

class ConfluentTabController(project: Project,
                             dataManager: KafkaDataManager) : TabbedDetailsMonitoringController<String>(project) {
  private val fieldsController = ConfluentSchemaFieldsController(project, dataManager)
  private val versionsController = ConfluentSchemaVersionsController(project, dataManager)

  override val tabsControllers = listOf(
    KafkaMessagesBundle.message("registry.tab.fields") to fieldsController,
    KafkaMessagesBundle.message("registry.tab.versions") to versionsController
  )

  init {
    init()
  }
}