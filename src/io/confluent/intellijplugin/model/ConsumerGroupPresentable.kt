package io.confluent.intellijplugin.model

import io.confluent.intellijplugin.core.monitoring.data.model.RemoteInfo
import io.confluent.intellijplugin.util.KafkaLocalizedField
import org.apache.kafka.common.GroupState

data class ConsumerGroupPresentable(
    val state: GroupState,
    val consumerGroup: String,
    val isFavorite: Boolean = false
) : RemoteInfo {

    companion object {
        val renderableColumns: List<KafkaLocalizedField<ConsumerGroupPresentable>> by lazy {
            listOf(
                KafkaLocalizedField(ConsumerGroupPresentable::state, "data.ConsumerGroupPresentable.state"),
                KafkaLocalizedField(
                    ConsumerGroupPresentable::consumerGroup,
                    "data.ConsumerGroupPresentable.consumerGroup"
                ),
                KafkaLocalizedField(ConsumerGroupPresentable::isFavorite, i18Key = null)
            )
        }
    }
}