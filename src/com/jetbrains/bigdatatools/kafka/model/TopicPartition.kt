package com.jetbrains.bigdatatools.kafka.model

import com.jetbrains.bigdatatools.common.monitoring.data.model.RemoteInfo
import com.jetbrains.bigdatatools.common.table.renderers.DataRenderingUtil
import com.jetbrains.bigdatatools.common.table.renderers.NoRendering
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaField

@Suppress("unused")
class TopicPartition(val partitionId: Int,
                     val leader: Int?,
                     @field:NoRendering
                     val replicas: List<InternalReplica>,
                     val inSyncReplicasCount: Int,
                     val replicasCount: Int) : RemoteInfo {
  companion object {
    val renderableColumns: List<KProperty1<TopicPartition, *>> by lazy {
      TopicPartition::class.declaredMemberProperties.filter { DataRenderingUtil.shouldRenderFrom(it.javaField?.annotations) }
    }
  }
}