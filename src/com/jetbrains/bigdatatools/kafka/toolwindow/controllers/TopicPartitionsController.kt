package com.jetbrains.bigdatatools.kafka.toolwindow.controllers

import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.model.TopicPartition
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings

class TopicPartitionsController(private val dataManager: KafkaDataManager) : AbstractTopicDetailController<TopicPartition>() {
  override fun getColumnSettings() = KafkaToolWindowSettings.getInstance().topicPartitionsColumnSettings

  override fun getRenderableColumns() = TopicPartition.renderableColumns

  override fun getModel(topicId: String) = dataManager.getTopicPartitionsModel(topicId)
}