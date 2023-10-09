package com.jetbrains.bigdatatools.kafka.model

import com.jetbrains.bigdatatools.common.monitoring.data.model.RemoteInfo
import com.jetbrains.bigdatatools.kafka.util.KafkaLocalizedField
import org.apache.kafka.common.ConsumerGroupState

data class ConsumerGroupPresentable(val state: ConsumerGroupState,
                                    val consumerGroup: String) : RemoteInfo {

  companion object {
    val renderableColumns: List<KafkaLocalizedField<ConsumerGroupPresentable>> by lazy {
      listOf(
        KafkaLocalizedField(ConsumerGroupPresentable::state, "data.ConsumerGroupPresentable.state"),
        KafkaLocalizedField(ConsumerGroupPresentable::consumerGroup, "data.ConsumerGroupPresentable.consumerGroup"),
      )
    }
  }
}