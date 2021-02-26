package com.jetbrains.bigdatatools.kafka.manager

import com.jetbrains.bigdatatools.kafka.model.InternalPartition
import com.jetbrains.bigdatatools.kafka.model.InternalReplica
import com.jetbrains.bigdatatools.kafka.model.InternalTopic
import com.jetbrains.bigdatatools.kafka.model.InternalTopicConfig
import org.apache.kafka.clients.admin.ConfigEntry
import org.apache.kafka.clients.admin.TopicDescription
import org.apache.kafka.common.TopicPartitionInfo


object BdtKafkaMapper {
  fun mapToInternalTopic(topicDescription: TopicDescription): InternalTopic {
    val partitions: List<InternalPartition> = topicDescription.partitions().map { partition: TopicPartitionInfo ->
      val replicas: List<InternalReplica> = partition.replicas().map {
        InternalReplica(it.id(), partition.leader().id() != it.id(), partition.isr().contains(it))
      }
      InternalPartition(leader = partition.leader()?.id(),
                        partition = partition.partition(),
                        inSyncReplicasCount = partition.isr().size,
                        replicasCount = partition.replicas().size,
                        replicas = replicas)
    }

    val underReplicatedPartitionsCount: Int = partitions.flatMap { it.replicas }.count { !it.inSync }
    val inSyncReplicasCount = partitions.sumBy { it.inSyncReplicasCount }

    val replicasCount = partitions.sumBy { it.replicasCount }
    val calcPartitions = partitions.associateBy { it.partition }
    val replicationFactor = topicDescription.partitions().firstOrNull()?.replicas()?.size ?: 0

    return InternalTopic(internal = topicDescription.isInternal,
                         name = topicDescription.name(),
                         partitions = calcPartitions,
                         replicas = replicasCount,
                         partitionCount = topicDescription.partitions().size,
                         inSyncReplicas = inSyncReplicasCount,
                         replicationFactor = replicationFactor,
                         underReplicatedPartitions = underReplicatedPartitionsCount)
  }


  fun mapToInternalTopicConfig(configEntry: ConfigEntry): InternalTopicConfig =
    InternalTopicConfig(name = configEntry.name(), value = configEntry.value())

  fun mergeWithConfigs(topics: List<InternalTopic>,
                       configs: Map<String, List<InternalTopicConfig>>): Map<String, InternalTopic> {
    return topics.map { t: InternalTopic ->
      t.copy(topicConfigs = (configs[t.name] ?: emptyList()))
    }.associateBy { it.name }
  }
}