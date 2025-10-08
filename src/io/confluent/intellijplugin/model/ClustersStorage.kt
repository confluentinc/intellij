package io.confluent.intellijplugin.model

import java.util.*
import java.util.concurrent.ConcurrentHashMap

class ClustersStorage {
    private val kafkaClusters: MutableMap<String, KafkaCluster> = ConcurrentHashMap()

    fun getKafkaClusters(): Collection<KafkaCluster> {
        return kafkaClusters.values
    }

    fun getClusterByName(clusterName: String?): Optional<KafkaCluster> {
        return Optional.ofNullable(kafkaClusters[clusterName])
    }

    fun setKafkaCluster(key: String, kafkaCluster: KafkaCluster) {
        kafkaClusters[key] = kafkaCluster
    }

    val kafkaClustersMap: Map<String, KafkaCluster>
        get() = kafkaClusters
}