package com.jetbrains.bigdatatools.kafka.client

import com.jetbrains.bigdatatools.kafka.model.BdtTopicPartition
import com.jetbrains.bigdatatools.kafka.model.ConsumerGroupPresentable
import com.jetbrains.bigdatatools.kafka.model.TopicConfig
import com.jetbrains.bigdatatools.kafka.model.TopicPresentable
import org.apache.kafka.clients.admin.ConfigEntry
import org.apache.kafka.clients.admin.ConsumerGroupDescription
import org.apache.kafka.clients.admin.TopicDescription
import org.apache.kafka.common.config.TopicConfig.MESSAGE_FORMAT_VERSION_CONFIG


object BdtKafkaMapper {
  fun mapToConsumerGroup(detailedGroup: ConsumerGroupDescription): ConsumerGroupPresentable {
    val topicsToPartitions = detailedGroup.members().flatMap {
      it.assignment().topicPartitions().map { topicPartition -> topicPartition.topic() to topicPartition.partition() }
    }.distinct()

    val numTopics = topicsToPartitions.map { it.first }.distinct().size
    val numTopicPartitions = topicsToPartitions.size

    return ConsumerGroupPresentable(state = detailedGroup.state(),
                                    consumerGroup = detailedGroup.groupId().ifBlank { "(blank)" },
                                    consumers = detailedGroup.members().size,
                                    topics = numTopics,
                                    partitions = numTopicPartitions)
  }

  fun topicDescriptionToInternalTopic(topicDescription: TopicDescription,
                                      partitions: List<BdtTopicPartition>): TopicPresentable {
    val underReplicatedPartitionsCount: Int = partitions.flatMap { it.internalReplicas }.count { !it.inSync }
    val inSyncReplicasCount = partitions.sumOf { it.inSyncReplicasCount }

    val replicasCount = partitions.sumOf { it.internalReplicas.size }
    val replicationFactor = topicDescription.partitions()?.firstOrNull()?.replicas()?.size ?: 0

    val messageCount = partitions.sumOf {
      val endOffset = it.endOffset ?: return@sumOf 0
      val startOffset = it.startOffset ?: return@sumOf 0
      endOffset - startOffset
    }

    return TopicPresentable(internal = topicDescription.isInternal,
                            name = topicDescription.name(),
                            partitionList = partitions,
                            replicas = replicasCount,
                            partitions = topicDescription.partitions()?.size ?: -1,
                            inSyncReplicas = inSyncReplicasCount,
                            replicationFactor = replicationFactor,
                            messageCount = messageCount,
                            underReplicatedPartitions = underReplicatedPartitionsCount)
  }


  fun mockInternalTopic(name: String) = TopicPresentable(name = name,
                                                         internal = false,
                                                         partitionList = emptyList(),
                                                         replicas = -1,
                                                         partitions = -1,
                                                         inSyncReplicas = -1,
                                                         replicationFactor = -1,
                                                         underReplicatedPartitions = -1,
                                                         messageCount = -1)

  fun mapToInternalTopicConfig(configEntry: ConfigEntry): TopicConfig {
    @Suppress("DEPRECATION")
    val defaultValue = if (configEntry.name() == MESSAGE_FORMAT_VERSION_CONFIG)
      configEntry.value()
    else
      KafkaConstants.TOPIC_DEFAULT_CONFIGS[configEntry.name()]

    return TopicConfig(name = configEntry.name(), value = configEntry.value(), defaultValue = defaultValue ?: "")
  }
}