package com.jetbrains.bigdatatools.kafka.model

import com.jetbrains.bigdatatools.common.monitoring.data.model.RemoteInfo
import com.jetbrains.bigdatatools.common.table.renderers.LoadingRendering
import com.jetbrains.bigdatatools.common.table.renderers.NoRendering
import com.jetbrains.bigdatatools.kafka.common.models.TopicInEditor
import com.jetbrains.bigdatatools.kafka.util.KafkaLocalizedField

data class TopicPresentable(val name: String,
                            @field:NoRendering
                            val internal: Boolean = false,
                            @field:NoRendering
                            val partitionList: List<BdtTopicPartition> = emptyList(),
                            @field:LoadingRendering(rightAligned = true)
                            val replicas: Int? = null,
                            @field:LoadingRendering(rightAligned = true)
                            val partitions: Int? = null,
                            @field:LoadingRendering
                            val inSyncReplicas: Int? = null,
                            @field:LoadingRendering(rightAligned = true)
                            val replicationFactor: Int? = null,
                            @field:LoadingRendering(rightAligned = true)
                            val underReplicatedPartitions: Int? = null,
                            val noLeaders: Int? = null,
                            @field:LoadingRendering(rightAligned = true)
                            val messageCount: Long? = null,
                            val isFavorite: Boolean = false) : RemoteInfo {

  fun toEditorTopic() = TopicInEditor(name)

  companion object {
    val renderableColumns: List<KafkaLocalizedField<TopicPresentable>> by lazy {
      listOf()
    }
  }
}