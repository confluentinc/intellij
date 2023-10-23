package com.jetbrains.bigdatatools.kafka.model

import com.jetbrains.bigdatatools.common.monitoring.data.model.RemoteInfo
import com.jetbrains.bigdatatools.common.table.renderers.NoRendering
import com.jetbrains.bigdatatools.kafka.util.KafkaLocalizedField

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