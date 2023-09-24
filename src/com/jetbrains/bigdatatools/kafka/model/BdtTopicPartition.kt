package com.jetbrains.bigdatatools.kafka.model

import com.jetbrains.bigdatatools.common.monitoring.data.model.RemoteInfo
import com.jetbrains.bigdatatools.common.table.renderers.NoRendering
import com.jetbrains.bigdatatools.kafka.util.KafkaLocalizedField

data class BdtTopicPartition(
  val topic: String,
  val partitionId: Int,
  val leader: Int?,
  @field:NoRendering
  val internalReplicas: List<InternalReplica>,
  val inSyncReplicasCount: Int,
  val replicas: String,
  val endOffset: Long?,
  val startOffset: Long?) : RemoteInfo {
  val offsets = "$startOffset -> $endOffset"

  companion object {
    val renderableColumns: List<KafkaLocalizedField<BdtTopicPartition>> by lazy {
      listOf(
        KafkaLocalizedField(BdtTopicPartition::topic, "data.BdtTopicPartition.topic"),
        KafkaLocalizedField(BdtTopicPartition::partitionId, "data.BdtTopicPartition.partitionId"),
        KafkaLocalizedField(BdtTopicPartition::leader, "data.BdtTopicPartition.leader"),
        KafkaLocalizedField(BdtTopicPartition::inSyncReplicasCount, "data.BdtTopicPartition.inSyncReplicasCount"),
        KafkaLocalizedField(BdtTopicPartition::replicas, "data.BdtTopicPartition.replicas"),
        KafkaLocalizedField(BdtTopicPartition::endOffset, "data.BdtTopicPartition.endOffset"),
        KafkaLocalizedField(BdtTopicPartition::startOffset, "data.BdtTopicPartition.startOffset"),
        KafkaLocalizedField(BdtTopicPartition::offsets, "data.BdtTopicPartition.offsets")
      )
    }
  }
}