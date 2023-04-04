package com.jetbrains.bigdatatools.kafka.registry.glue.controller

import com.intellij.openapi.project.Project
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.TabbedDetailsMonitoringController
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import software.amazon.awssdk.services.glue.model.SchemaId

class GlueTabController(project: Project,
                        dataManager: KafkaDataManager) : TabbedDetailsMonitoringController<SchemaId>(project) {
  private val versionsController = GlueSchemaVersionsController(project, dataManager)
  private val schemaInfoController = GlueSchemaInfoController(project, dataManager)

  override val tabsControllers = listOf(
    KafkaMessagesBundle.message("registry.tab.schema.info") to schemaInfoController,
    KafkaMessagesBundle.message("registry.tab.versions") to versionsController
  )

  init {
    init()
  }
}