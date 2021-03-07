package com.jetbrains.bigdatatools.kafka.toolwindow.controllers

import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.model.TopicConfig
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings

class TopicConfigsController(private val dataManager: KafkaDataManager) : AbstractTopicDetailController<TopicConfig>() {
  override fun getColumnSettings() = KafkaToolWindowSettings.getInstance().topicConfigsColumnSettings

  override fun getRenderableColumns() = TopicConfig.renderableColumns

  override fun getModel(topicId: String) = dataManager.getTopicConfigsModel(topicId)
}