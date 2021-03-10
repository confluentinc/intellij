package com.jetbrains.bigdatatools.kafka.model

import com.jetbrains.bigdatatools.monitoring.data.model.RemoteInfo
import com.jetbrains.bigdatatools.table.renderers.DataRenderingUtil
import com.jetbrains.bigdatatools.table.renderers.NoRendering
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaField

@Suppress("unused")
class TopicPartition(val partitionId: Int = 0,
                     val leader: Int? = null,
                     @field:NoRendering
                     val replicas: List<InternalReplica> = emptyList(),
                     val inSyncReplicasCount: Int = 0,
                     val replicasCount: Int = 0,
                     val offsetMin: Long = 0,
                     val offsetMax: Long = 0,
                     val segmentSize: Long = 0,
                     val segmentCount: Long = 0) : RemoteInfo {
  companion object {
    val renderableColumns: List<KProperty1<TopicPartition, *>> by lazy {
      TopicPartition::class.declaredMemberProperties.filter { DataRenderingUtil.shouldRenderFrom(it.javaField?.annotations) }
    }
  }
}