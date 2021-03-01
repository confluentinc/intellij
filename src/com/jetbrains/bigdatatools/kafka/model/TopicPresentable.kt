package com.jetbrains.bigdatatools.kafka.model

import com.jetbrains.bigdatatools.monitoring.data.model.RemoteInfo
import com.jetbrains.bigdatatools.table.renderers.DataRenderingUtil
import com.jetbrains.bigdatatools.table.renderers.NoRendering
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaField

data class TopicPresentable(val name: String = "",
                            val internal: Boolean = false,
                            @field:NoRendering
                            val partitions: Map<Int, InternalPartition> = mapOf(),
                            @field:NoRendering
                            val topicConfigs: List<InternalTopicConfig> = listOf(),
                            val replicas: Int = 0,
                            val partitionCount: Int = 0,
                            val inSyncReplicas: Int = 0,
                            val replicationFactor: Int = 0,
                            val underReplicatedPartitions: Int = 0) : RemoteInfo {
  companion object {
    val renderableColumns: List<KProperty1<TopicPresentable, *>> by lazy {
      TopicPresentable::class.declaredMemberProperties.filter { DataRenderingUtil.shouldRenderFrom(it.javaField?.annotations) }
    }
  }
}