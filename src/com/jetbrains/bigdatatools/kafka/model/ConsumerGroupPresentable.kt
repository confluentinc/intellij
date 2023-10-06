package com.jetbrains.bigdatatools.kafka.model

import com.jetbrains.bigdatatools.common.monitoring.data.model.RemoteInfo
import com.jetbrains.bigdatatools.common.table.renderers.LoadingRendering
import com.jetbrains.bigdatatools.common.table.renderers.NoRendering
import com.jetbrains.bigdatatools.kafka.util.KafkaLocalizedField
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import org.apache.kafka.common.ConsumerGroupState

data class ConsumerGroupPresentable(val state: ConsumerGroupState,
                                    val consumerGroup: String,
                                    @field:LoadingRendering(rightAligned = true)
                                    val consumers: Int? = null,
                                    @field:NoRendering
                                    val topicValues: List<String>? = null,
                                    @field:LoadingRendering
                                    val partitions: Int? = null) : RemoteInfo {
  @field:LoadingRendering
  val topics: String? = when {
    topicValues == null -> null
    topicValues.isEmpty() -> KafkaMessagesBundle.message("consumer.group.topics.empty")
    else -> topicValues.joinToString(separator = ", ") { it }
  }


  companion object {
    val renderableColumns: List<KafkaLocalizedField<ConsumerGroupPresentable>> by lazy {
      listOf(
        KafkaLocalizedField(ConsumerGroupPresentable::state, "data.ConsumerGroupPresentable.state"),
        KafkaLocalizedField(ConsumerGroupPresentable::consumerGroup, "data.ConsumerGroupPresentable.consumerGroup"),
        KafkaLocalizedField(ConsumerGroupPresentable::consumers, "data.ConsumerGroupPresentable.consumers"),
        KafkaLocalizedField(ConsumerGroupPresentable::topics, "data.ConsumerGroupPresentable.topics"),
        KafkaLocalizedField(ConsumerGroupPresentable::partitions, "data.ConsumerGroupPresentable.partitions")
      )
    }
  }
}