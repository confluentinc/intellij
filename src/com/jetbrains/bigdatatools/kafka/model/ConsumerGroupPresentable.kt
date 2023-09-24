package com.jetbrains.bigdatatools.kafka.model

import com.jetbrains.bigdatatools.common.monitoring.data.model.RemoteInfo
import com.jetbrains.bigdatatools.common.table.renderers.LoadingRendering
import com.jetbrains.bigdatatools.kafka.util.KafkaLocalizedField
import org.apache.kafka.common.ConsumerGroupState

data class ConsumerGroupPresentable(val state: ConsumerGroupState,
                                    val consumerGroup: String,
                                    @field:LoadingRendering(rightAligned = true)
                                    val consumers: Int? = null,
                                    @field:LoadingRendering(rightAligned = true)
                                    val topics: Int? = null,
                                    @field:LoadingRendering
                                    val partitions: Int? = null) : RemoteInfo {
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