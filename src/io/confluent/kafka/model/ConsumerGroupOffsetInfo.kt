package io.confluent.kafka.model

import io.confluent.kafka.core.monitoring.data.model.RemoteInfo
import io.confluent.kafka.core.table.renderers.NoRendering
import io.confluent.kafka.util.KafkaLocalizedField

data class ConsumerGroupOffsetInfo(val topic: String,
                                   val partition: Int,
                                   val offset: Long,
                                   val lag: Long?) : RemoteInfo {

  @NoRendering
  val fullId = topic + partition

  companion object {
    val renderableColumns: List<KafkaLocalizedField<ConsumerGroupOffsetInfo>> by lazy {
      listOf(
        KafkaLocalizedField(ConsumerGroupOffsetInfo::topic, "data.BdtTopicPartition.topic"),
        KafkaLocalizedField(ConsumerGroupOffsetInfo::partition, "output.column.partition"),
        KafkaLocalizedField(ConsumerGroupOffsetInfo::offset, "output.column.offset"),
        KafkaLocalizedField(ConsumerGroupOffsetInfo::lag, "consumer.group.lag")
      )
    }
  }
}