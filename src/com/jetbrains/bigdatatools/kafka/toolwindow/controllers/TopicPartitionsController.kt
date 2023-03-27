package com.jetbrains.bigdatatools.kafka.toolwindow.controllers

import com.jetbrains.bigdatatools.common.monitoring.toolwindow.DetailsTableMonitoringController
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.model.TopicPartition
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings

class TopicPartitionsController(private val dataManager: KafkaDataManager) : DetailsTableMonitoringController<TopicPartition, String>() {
  init {
    init()
  }

  override fun getColumnSettings() = KafkaToolWindowSettings.getInstance().topicPartitionsColumnSettings

  override fun getRenderableColumns() = TopicPartition.renderableColumns

  override fun getDataModel() = selectedId?.let { dataManager.topicPartitionsModels.get(it) }

  override fun showColumnFilter(): Boolean = false
}