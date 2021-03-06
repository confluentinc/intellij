package com.jetbrains.bigdatatools.kafka.client

import com.jetbrains.bigdatatools.kafka.model.*
import org.apache.kafka.clients.admin.ConfigEntry
import org.apache.kafka.clients.admin.ConsumerGroupDescription
import org.apache.kafka.clients.admin.TopicDescription
import org.apache.kafka.common.TopicPartitionInfo


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

  fun mapToInternalTopic(topicDescription: TopicDescription): TopicPresentable {
    val partitions: List<TopicPartition> = topicDescription.partitions().map { partition: TopicPartitionInfo ->
      val replicas: List<InternalReplica> = partition.replicas().map {
        InternalReplica(it.id(), partition.leader().id() != it.id(), partition.isr().contains(it))
      }
      TopicPartition(leader = partition.leader()?.id(),
                     partition = partition.partition(),
                     inSyncReplicasCount = partition.isr().size,
                     replicasCount = partition.replicas().size,
                     replicas = replicas)
    }

    val underReplicatedPartitionsCount: Int = partitions.flatMap { it.replicas }.count { !it.inSync }
    val inSyncReplicasCount = partitions.sumBy { it.inSyncReplicasCount }

    val replicasCount = partitions.sumBy { it.replicasCount }
    val replicationFactor = topicDescription.partitions().firstOrNull()?.replicas()?.size ?: 0

    return TopicPresentable(internal = topicDescription.isInternal,
                            name = topicDescription.name(),
                            partitions = partitions,
                            replicas = replicasCount,
                            partitionCount = topicDescription.partitions().size,
                            inSyncReplicas = inSyncReplicasCount,
                            replicationFactor = replicationFactor,
                            underReplicatedPartitions = underReplicatedPartitionsCount)
  }


  fun mapToInternalTopicConfig(configEntry: ConfigEntry): TopicConfigPresentable =
    TopicConfigPresentable(name = configEntry.name(), value = configEntry.value())

  fun mergeWithConfigs(topics: List<TopicPresentable>,
                       configs: Map<String, List<TopicConfigPresentable>>): Map<String, TopicPresentable> {
    return topics.map { t: TopicPresentable ->
      t.copy(topicConfigs = (configs[t.name] ?: emptyList()))
    }.associateBy { it.name }
  }
}