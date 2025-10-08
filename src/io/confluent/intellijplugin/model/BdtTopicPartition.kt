package io.confluent.intellijplugin.model

import io.confluent.intellijplugin.core.monitoring.data.model.RemoteInfo
import io.confluent.intellijplugin.core.table.renderers.NoRendering
import io.confluent.intellijplugin.util.KafkaLocalizedField

data class BdtTopicPartition(
    val topic: String,
    val partitionId: Int,
    val leader: Int?,
    @field:NoRendering
    val internalReplicas: List<InternalReplica>,
    val inSyncReplicasCount: Int,
    val replicas: String,
    val endOffset: Long?,
    val startOffset: Long?,
) : RemoteInfo {
    val messageCount: Long? = if (startOffset != null && endOffset != null)
        endOffset - startOffset
    else
        null

    companion object {
        val renderableColumns: List<KafkaLocalizedField<BdtTopicPartition>> by lazy {
            listOf(
                KafkaLocalizedField(BdtTopicPartition::topic, "data.BdtTopicPartition.topic"),
                KafkaLocalizedField(BdtTopicPartition::partitionId, "data.BdtTopicPartition.partitionId"),
                KafkaLocalizedField(BdtTopicPartition::messageCount, "data.TopicPresentable.messageCount"),
                KafkaLocalizedField(BdtTopicPartition::startOffset, "data.BdtTopicPartition.startOffset"),
                KafkaLocalizedField(BdtTopicPartition::endOffset, "data.BdtTopicPartition.endOffset"),
                KafkaLocalizedField(BdtTopicPartition::leader, "data.BdtTopicPartition.leader"),
                KafkaLocalizedField(
                    BdtTopicPartition::inSyncReplicasCount,
                    "data.BdtTopicPartition.inSyncReplicasCount"
                ),
                KafkaLocalizedField(BdtTopicPartition::replicas, "data.BdtTopicPartition.replicas"),
            )
        }
    }
}