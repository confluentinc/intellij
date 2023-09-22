package com.jetbrains.bigdatatools.kafka.model

import com.jetbrains.bigdatatools.common.monitoring.data.model.RemoteInfo
import com.jetbrains.bigdatatools.common.table.renderers.NoRendering
import com.jetbrains.bigdatatools.kafka.util.KafkaLocalizedField

@Suppress("unused")
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
      listOf()
    }
  }
}