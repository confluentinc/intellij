package com.jetbrains.bigdatatools.kafka.model

import com.jetbrains.bigdatatools.common.monitoring.data.model.RemoteInfo
import com.jetbrains.bigdatatools.common.table.renderers.DataRenderingUtil
import com.jetbrains.bigdatatools.common.table.renderers.LoadingRendering
import com.jetbrains.bigdatatools.common.table.renderers.NoRendering
import com.jetbrains.bigdatatools.kafka.common.models.TopicInEditor
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaField

data class TopicPresentable(val name: String,
                            @field:NoRendering
                            val internal: Boolean = false,
                            @field:NoRendering
                            val partitionList: List<BdtTopicPartition> = emptyList(),
                            @field:LoadingRendering
                            val replicas: Int? = null,
                            @field:LoadingRendering
                            val partitions: Int? = null,
                            @field:LoadingRendering
                            val inSyncReplicas: Int? = null,
                            @field:LoadingRendering
                            val replicationFactor: Int? = null,
                            @field:LoadingRendering
                            val underReplicatedPartitions: Int? = null,
                            @field:LoadingRendering
                            val messageCount: Long? = null) : RemoteInfo {
  fun toEditorTopic() = TopicInEditor(name)

  companion object {
    val renderableColumns: List<KProperty1<TopicPresentable, *>> by lazy {
      TopicPresentable::class.declaredMemberProperties.filter { DataRenderingUtil.shouldRenderFrom(it.javaField?.annotations) }
    }
  }
}