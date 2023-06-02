package com.jetbrains.bigdatatools.kafka.model

import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import org.jetbrains.annotations.Nls

data class TopicStatisticInfo(val topicCount: Int,
                              val partitionCount: Int,
                              val urp: Int,
                              val noLeader: Int) {
  @Nls
  override fun toString(): String = if (topicCount != 0 && partitionCount == 0)
    ""
  else
    KafkaMessagesBundle.message("topics.0.partitions.1.urp.2.no.leader.3", topicCount, partitionCount, urp, noLeader)

  companion object {
    fun createFor(topics: List<TopicPresentable>): TopicStatisticInfo = TopicStatisticInfo(
      topicCount = topics.size,
      partitionCount = topics.sumOf { it.partitions ?: 0 },
      urp = topics.sumOf { it.underReplicatedPartitions ?: 0 },
      noLeader = topics.sumOf { it.noLeaders ?: 0 },
    )
  }
}