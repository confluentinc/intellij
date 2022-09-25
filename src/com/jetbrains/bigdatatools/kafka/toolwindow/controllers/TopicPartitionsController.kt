package com.jetbrains.bigdatatools.kafka.toolwindow.controllers

import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.model.TopicPartition
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.common.monitoring.toolwindow.DetailsTableMonitoringController

class TopicPartitionsController(private val dataManager: KafkaDataManager) : DetailsTableMonitoringController<TopicPartition>() {
  init {
    init()
  }

  override fun getColumnSettings() = KafkaToolWindowSettings.getInstance().topicPartitionsColumnSettings

  override fun getRenderableColumns() = TopicPartition.renderableColumns

  override fun getDataModel() = selectedId?.let { dataManager.getTopicPartitionsModel(it) }
}