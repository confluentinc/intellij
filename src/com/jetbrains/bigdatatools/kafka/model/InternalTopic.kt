package com.jetbrains.bigdatatools.kafka.model


data class InternalTopic(
  val name: String = "",
  val internal: Boolean = false,
  val partitions: Map<Int, InternalPartition> = mapOf(),
  val topicConfigs: List<InternalTopicConfig> = listOf(),
  val replicas: Int = 0,
  val partitionCount: Int = 0,
  val inSyncReplicas: Int = 0,
  val replicationFactor: Int = 0,
  val underReplicatedPartitions: Int = 0)