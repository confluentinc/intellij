package io.confluent.kafka.model

import io.confluent.kafka.core.monitoring.data.model.RemoteInfo
import io.confluent.kafka.util.KafkaLocalizedField
import org.apache.kafka.common.ConsumerGroupState

data class ConsumerGroupPresentable(val state: ConsumerGroupState,
                                    val consumerGroup: String,
                                    val isFavorite: Boolean = false) : RemoteInfo {

  companion object {
    val renderableColumns: List<KafkaLocalizedField<ConsumerGroupPresentable>> by lazy {
      listOf(
        KafkaLocalizedField(ConsumerGroupPresentable::state, "data.ConsumerGroupPresentable.state"),
        KafkaLocalizedField(ConsumerGroupPresentable::consumerGroup, "data.ConsumerGroupPresentable.consumerGroup"),
        KafkaLocalizedField(ConsumerGroupPresentable::isFavorite, i18Key = null)
      )
    }
  }
}