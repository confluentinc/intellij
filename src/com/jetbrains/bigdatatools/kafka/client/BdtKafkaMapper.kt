package com.jetbrains.bigdatatools.kafka.client

import com.jetbrains.bigdatatools.kafka.model.*
import org.apache.kafka.clients.admin.ConfigEntry
import org.apache.kafka.clients.admin.ConsumerGroupDescription
import org.apache.kafka.clients.admin.ListOffsetsResult
import org.apache.kafka.clients.admin.TopicDescription
import org.apache.kafka.common.TopicPartitionInfo
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
                                      earliestOffsets: Map<org.apache.kafka.common.TopicPartition, ListOffsetsResult.ListOffsetsResultInfo>?,
                                      latestOffsets: Map<org.apache.kafka.common.TopicPartition, ListOffsetsResult.ListOffsetsResultInfo>?): TopicPresentable {
    val startPartitionOffsets = earliestOffsets?.map { it.key.partition() to it.value.offset() }?.toMap() ?: emptyMap()
    val endPartitionOffsets = latestOffsets?.map { it.key.partition() to it.value.offset() }?.toMap() ?: emptyMap()

    val partitions: List<TopicPartition> = topicDescription.partitions()?.map { partition: TopicPartitionInfo ->
      val replicas: List<InternalReplica> = partition.replicas().filterNotNull().map {
        InternalReplica(it.id(), partition.leader()?.id() != it.id(), partition.isr()?.contains(it) == true)
      }
      partition.replicas().map { }
      val partitionId = partition.partition()
      TopicPartition(leader = partition.leader()?.id(),
                     partitionId = partitionId,
                     inSyncReplicasCount = partition.isr().size,
                     replicas = partition.replicas()?.joinToString(separator = ", ") { it.idString() } ?: "",
                     startOffset = startPartitionOffsets[partitionId],
                     endOffset = endPartitionOffsets[partitionId],
                     internalReplicas = replicas)
    } ?: emptyList()

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
                            underReplicatedPartitions = underReplicatedPartitionsCount,
                            topicConfigs = emptyList()
    )
  }

  fun mockInternalTopic(name: String) = TopicPresentable(name = name,
                                                         internal = false,
                                                         partitionList = emptyList(),
                                                         topicConfigs = emptyList(),
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

  fun mergeWithConfigs(topics: List<TopicPresentable>,
                       configs: Map<String, List<TopicConfig>>): Map<String, TopicPresentable> {
    return topics.map { t: TopicPresentable ->
      t.copy(topicConfigs = (configs[t.name] ?: emptyList()))
    }.associateBy { it.name }
  }
}