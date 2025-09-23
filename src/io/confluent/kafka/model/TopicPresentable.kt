package io.confluent.kafka.model

import io.confluent.kafka.common.models.TopicInEditor
import io.confluent.kafka.core.monitoring.data.model.RemoteInfo
import io.confluent.kafka.core.table.renderers.LoadingRendering
import io.confluent.kafka.core.table.renderers.NoRendering
import io.confluent.kafka.util.KafkaLocalizedField

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
      listOf(
        KafkaLocalizedField(TopicPresentable::name, "data.TopicPresentable.name"),
        KafkaLocalizedField(TopicPresentable::replicas, "data.TopicPresentable.replicas"),
        KafkaLocalizedField(TopicPresentable::partitions, "data.TopicPresentable.partitions"),
        KafkaLocalizedField(TopicPresentable::inSyncReplicas, "data.TopicPresentable.inSyncReplicas"),
        KafkaLocalizedField(TopicPresentable::replicationFactor, "data.TopicPresentable.replicationFactor"),
        KafkaLocalizedField(TopicPresentable::underReplicatedPartitions, "data.TopicPresentable.underReplicatedPartitions"),
        KafkaLocalizedField(TopicPresentable::noLeaders, "data.TopicPresentable.noLeaders"),
        KafkaLocalizedField(TopicPresentable::messageCount, "data.TopicPresentable.messageCount"),
        KafkaLocalizedField(TopicPresentable::isFavorite, i18Key = null)
      )
    }
  }
}