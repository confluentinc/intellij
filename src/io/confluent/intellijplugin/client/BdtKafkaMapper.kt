package io.confluent.intellijplugin.client

import io.confluent.intellijplugin.model.BdtTopicPartition
import io.confluent.intellijplugin.model.TopicConfig
import io.confluent.intellijplugin.model.TopicPresentable
import io.confluent.intellijplugin.registry.KafkaRegistryFormat
import io.confluent.intellijplugin.registry.common.KafkaSchemaInfo
import org.apache.kafka.clients.admin.ConfigEntry
import org.apache.kafka.clients.admin.TopicDescription
import org.apache.kafka.common.config.TopicConfig.MESSAGE_FORMAT_VERSION_CONFIG


object BdtKafkaMapper {

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
                            underReplicatedPartitions = underReplicatedPartitionsCount,
                            noLeaders = partitions.count { it.leader == null })
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

  fun mockKafkaSchemaInfo(info: KafkaSchemaInfo): KafkaSchemaInfo {
    return info.copy(version = -1, type = KafkaRegistryFormat.UNKNOWN, compatibility = "", description = "", schemaStatus = "")
  }
}