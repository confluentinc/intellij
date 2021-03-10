package com.jetbrains.bigdatatools.kafka.model

import com.jetbrains.bigdatatools.monitoring.data.model.RemoteInfo
import com.jetbrains.bigdatatools.table.renderers.DataRenderingUtil
import com.jetbrains.bigdatatools.table.renderers.NoRendering
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaField

data class TopicPresentable(val name: String,
                            @field:NoRendering
                            val internal: Boolean,
                            @field:NoRendering
                            val partitionList: List<TopicPartition>,
                            @field:NoRendering
                            val topicConfigs: List<TopicConfig>,
                            val replicas: Int,
                            val partitions: Int,
                            val inSyncReplicas: Int,
                            val replicationFactor: Int,
                            val underReplicatedPartitions: Int) : RemoteInfo {
  companion object {
    val renderableColumns: List<KProperty1<TopicPresentable, *>> by lazy {
      TopicPresentable::class.declaredMemberProperties.filter { DataRenderingUtil.shouldRenderFrom(it.javaField?.annotations) }
    }
  }
}