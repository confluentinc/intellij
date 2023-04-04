package com.jetbrains.bigdatatools.kafka.registry.glue.controller

import com.jetbrains.bigdatatools.common.monitoring.toolwindow.DetailsTableMonitoringController
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.model.SchemaRegistryFieldsInfo
import com.jetbrains.bigdatatools.kafka.registry.glue.models.SchemaVersionId
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings

class GlueSchemaFieldsController(private val dataManager: KafkaDataManager) : DetailsTableMonitoringController<SchemaRegistryFieldsInfo, SchemaVersionId>() {

  init {
    init()
  }

  override fun showColumnFilter(): Boolean = false
  override fun getColumnSettings() = KafkaToolWindowSettings.getInstance().schemaRegistryFieldsTableColumnSettings
  override fun getRenderableColumns() = SchemaRegistryFieldsInfo.renderableColumns
  override fun getDataModel() = selectedId?.let {
    dataManager.glueSchemaRegistry?.getRegistrySchemaFieldsModel(it)
  }
}