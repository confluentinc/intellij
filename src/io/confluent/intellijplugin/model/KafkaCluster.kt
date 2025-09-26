package io.confluent.intellijplugin.model

import java.nio.file.Path
import java.util.*


data class KafkaCluster(val name: String = "",
                        val jmxPort: Int = -1,
                        val bootstrapServers: String = "",
                        val zookeeper: String = "",
                        val schemaRegistry: String = "",
                        val kafkaConnect: List<KafkaConnectCluster> = emptyList(),
                        val schemaNameTemplate: String = "",
                        val status: ServerStatus = ServerStatus.OFFLINE,
                        val zookeeperStatus: ServerStatus = ServerStatus.OFFLINE,
                        val metrics: InternalClusterMetrics = InternalClusterMetrics(),
                        val topics: Map<String, TopicPresentable> = emptyMap(),
                        val lastKafkaException: Throwable? = null,
                        val lastZookeeperException: Throwable? = null,
                        val protobufFile: Path? = null,
                        val protobufMessageName: String? = null,
                        val properties: Properties? = null)