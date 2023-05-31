package com.jetbrains.bigdatatools.kafka.model

import com.jetbrains.bigdatatools.common.monitoring.data.model.RemoteInfo
import com.jetbrains.bigdatatools.common.table.renderers.DataRenderingUtil
import com.jetbrains.bigdatatools.common.table.renderers.NoRendering
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaField

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
    val renderableColumns: List<KProperty1<BdtTopicPartition, *>> by lazy {
      BdtTopicPartition::class.declaredMemberProperties.filter { DataRenderingUtil.shouldRenderFrom(it.javaField?.annotations) }
    }
  }
}