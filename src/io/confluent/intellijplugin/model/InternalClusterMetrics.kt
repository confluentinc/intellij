package io.confluent.intellijplugin.model

import org.apache.kafka.common.Metric
import java.math.BigDecimal

class InternalClusterMetrics(val brokerCount: Int = 0,
                             val topicCount: Int = 0,
                             val activeControllers: Int = 0,
                             val uncleanLeaderElectionCount: Int = 0,
                             val onlinePartitionCount: Int = 0,
                             val underReplicatedPartitionCount: Int = 0,
                             val offlinePartitionCount: Int = 0,
                             val inSyncReplicasCount: Int = 0,
                             val outOfSyncReplicasCount: Int = 0,
                             val bytesInPerSec: Map<String, BigDecimal> = mapOf(),
                             val bytesOutPerSec: Map<String, BigDecimal> = mapOf(),
                             val segmentCount: Long = 0,
                             val segmentSize: Long = 0,
                             val internalBrokerDiskUsage: Map<Int, InternalBrokerDiskUsage> = mapOf(),
                             val internalBrokerMetrics: Map<Int, InternalBrokerMetrics> = mapOf(),
                             val metrics: List<Metric>? = null,
                             val zooKeeperStatus: Int = 0)